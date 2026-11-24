package dev.shizzi;

import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AdbStream;

/**
 * Starts TetherMain through the device's own Wireless debugging (adbd over TLS on this phone), so
 * no Shizuku and no PC are needed. Wireless debugging only has to be on (and Wi-Fi connected)
 * while the helper is being started; the helper is a detached process that keeps running
 * afterwards.
 */
public final class AdbHelper {

    static final String LOG_FILE = "/data/local/tmp/shizzi_helper.log";
    private static final long CONNECT_TIMEOUT_MS = 10_000;
    private static final long BINDER_TIMEOUT_S = 25;

    /** Thrown with a short machine-readable code the WebUI maps to a message. */
    static final class AdbFailure extends Exception {
        final String code;

        AdbFailure(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private AdbHelper() {
    }

    static boolean isPaired(Context c) {
        return AdbBridge.isPaired(c);
    }

    /** Pairing with the code from Settings > Developer options > Wireless debugging > Pair device. */
    static void pair(Context c, int port, String code) throws Exception {
        AdbBridge b = AdbBridge.get(c);
        boolean ok = b.pair("127.0.0.1", port, code);
        if (!ok) throw new AdbFailure("adb_pair_failed", c.getString(R.string.adb_pair_refused));
        AdbBridge.markPaired(c, true);
    }

    /**
     * Returns the running helper, or starts one through adb. An already-running helper (the app
     * process was killed, the helper was not) re-announces itself within a few seconds, so that is
     * awaited first: a second helper would retire the first together with its live session.
     */
    static ITetherService launch(Context app) throws Exception {
        ITetherService live = AdbHelperRegistry.INSTANCE.live();
        if (live != null) return live;
        if (!AdbBridge.isPaired(app)) throw new AdbFailure("adb_not_paired", app.getString(R.string.adb_not_paired));

        if (helperLaunchedThisBoot(app)) {
            live = AdbHelperRegistry.INSTANCE.awaitExisting(4_000);
            if (live != null) return live;
        }

        String secret = AdbHelperRegistry.INSTANCE.secret(app);
        CompletableFuture<IBinder> binderFuture = AdbHelperRegistry.INSTANCE.expectNew();
        AdbBridge b = AdbBridge.get(app);
        try {
            boolean connected = false;
            // 1. The classic port a previous start left open (TCP mode): no Wi-Fi, no Wireless debugging.
            int active = AdbBridge.tcpActivePort(app);
            if (active > 0 && AdbPorts.listening(active)) connected = tryConnect(b, active, 2);
            // 2. Otherwise Wireless debugging, which needs Wi-Fi: the first start after every boot.
            if (!connected) {
                connectWireless(app, b);
                // 3. TCP mode: open the classic port now, BEFORE the helper starts. `tcpip` restarts
                //    adbd and takes down anything it spawned, so a helper started first would die.
                if (AdbBridge.isTcpMode(app)) {
                    int tcpPort = AdbBridge.isTcpRandom(app) ? randomPort() : AdbBridge.tcpPort(app);
                    boolean onTcp = openTcpPort(b, tcpPort);
                    closeQuietly(b);
                    if (onTcp) {
                        AdbBridge.setTcpActivePort(app, tcpPort);
                        onTcp = tryConnect(b, tcpPort, 6);
                    }
                    if (!onTcp) connectWireless(app, b);     // adbd restarted underneath us: back to wireless
                }
            }

            String apk = app.getApplicationInfo().sourceDir;
            String abi = Process.is64Bit()
                    ? (Build.SUPPORTED_64_BIT_ABIS.length > 0 ? Build.SUPPORTED_64_BIT_ABIS[0] : "arm64-v8a")
                    : (Build.SUPPORTED_32_BIT_ABIS.length > 0 ? Build.SUPPORTED_32_BIT_ABIS[0] : "armeabi-v7a");
            // The Go datapath is a native library: a bare app_process has no app class loader to find it.
            String libs = apk + "!/lib/" + abi + ":" + app.getApplicationInfo().nativeLibraryDir;
            String cmd = "(CLASSPATH='" + apk + "' setsid " + (Process.is64Bit() ? "app_process64" : "app_process32")
                    + " -Djava.library.path='" + libs + "' /system/bin"
                    + " --nice-name=dev.shizzi:adb " + TetherMain.class.getName()
                    + " " + app.getPackageName() + " " + secret
                    + " >" + LOG_FILE + " 2>&1 </dev/null &); echo launched uid=$(id -u)";
            String out = run(b, cmd);
            // The reply is only a hint: the stream may close before it is read. The real proof is the
            // helper's broadcast below. A reply naming another uid means adbd is not the shell.
            if (out.contains("uid=") && !out.contains("uid=2000")) {
                throw new AdbFailure("adb_launch_failed", app.getString(R.string.adb_launch_failed) + ": " + out.trim());
            }
        } finally {
            try {
                b.close();                       // the detached helper does not depend on this session
            } catch (Throwable ignored) {
            }
        }

        try {
            IBinder binder = binderFuture.get(BINDER_TIMEOUT_S, TimeUnit.SECONDS);
            markLaunched(app);
            return ITetherService.Stub.asInterface(binder);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AdbFailure("adb_no_binder", app.getString(R.string.adb_no_binder, LOG_FILE));
        } finally {
            AdbHelperRegistry.INSTANCE.cancelWaiting();
        }
    }

    /** Wireless debugging (TLS) connection; throws AdbFailure when there is none. */
    private static void connectWireless(Context app, AdbBridge b) throws AdbFailure {
        boolean connected = false;
        String why = "";
        int port = AdbPorts.findConnectPort(app, CONNECT_TIMEOUT_MS);
        if (port > 0) {
            try {
                connected = b.connect("127.0.0.1", port);
            } catch (Exception e) {
                why = e.getMessage();
            }
        }
        if (!connected) {                        // last resort: the library's own discovery
            try {
                connected = b.autoConnect(app, CONNECT_TIMEOUT_MS);
            } catch (Exception e) {
                if (why == null || why.isEmpty()) why = e.getMessage();
            }
        }
        if (!connected) {
            throw new AdbFailure("adb_connect_failed",
                    app.getString(R.string.adb_no_port)
                            + (why == null || why.isEmpty() ? "" : ": " + why));
        }
    }

    private static boolean tryConnect(AdbBridge b, int port, int attempts) {
        for (int i = 0; i < attempts; i++) {
            try {
                if (b.connect("127.0.0.1", port)) return true;
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Asks adbd (over the live wireless connection) to also listen on the given port, and waits for it. */
    private static int randomPort() {
        java.util.Random r = new java.security.SecureRandom();
        for (int i = 0; i < 50; i++) {
            int p = 1024 + r.nextInt(65536 - 1024);
            if (!AdbPorts.listening(p)) return p;
        }
        return 1024 + r.nextInt(65536 - 1024);
    }

    private static boolean openTcpPort(AdbBridge b, int port) {
        AdbStream s = null;
        try {
            s = b.openStream("tcpip:" + port);
            InputStream in = s.openInputStream();
            byte[] buf = new byte[256];
            while (in.read(buf) > 0) {
                // "restarting in TCP mode port: 5555"; adbd then drops the connection
            }
        } catch (Exception expected) {
            // the connection dying is how adbd confirms the restart
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Throwable ignored) {
                }
            }
        }
        for (int i = 0; i < 40; i++) {           // up to 10 s
            if (AdbPorts.listening(port)) return true;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static void closeQuietly(AdbBridge b) {
        try {
            b.close();
        } catch (Throwable ignored) {
        }
    }

    private static String run(AdbBridge b, String cmd) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        AdbStream s = null;
        try {
            s = b.openStream("shell:" + cmd);
            InputStream in = s.openInputStream();
            byte[] buf = new byte[1024];
            int n;
            while (o.size() < 8192 && (n = in.read(buf)) > 0) o.write(buf, 0, n);
        } catch (java.io.IOException closed) {
            // "Stream closed" is how the library reports the shell ending: not an error here
        } catch (Exception ignored) {
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            return o.toString("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    // A helper started in this boot may still be alive: its restart marker is the boot count.
    private static boolean helperLaunchedThisBoot(Context c) {
        return c.getSharedPreferences("adb_helper", Context.MODE_PRIVATE).getInt("boot", -1) == bootCount(c);
    }

    private static void markLaunched(Context c) {
        c.getSharedPreferences("adb_helper", Context.MODE_PRIVATE).edit().putInt("boot", bootCount(c)).apply();
    }

    private static int bootCount(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT);
        } catch (Throwable t) {
            return -2;
        }
    }
}
