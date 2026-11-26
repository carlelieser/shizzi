package dev.shizzi;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds the port of this phone's own Wireless debugging (TLS connect) server. libadb's autoConnect
 * relies on one mDNS query and times out on some devices, so two independent sources are tried
 * together: the service.adb.tls.port property (where the platform lets an app read it) and an
 * NsdManager search of _adb-tls-connect._tcp. A port only counts once something accepts on it.
 */
final class AdbPorts {

    private static final String TLS_CONNECT = "_adb-tls-connect._tcp";

    private AdbPorts() {
    }

    /** @return the port, or -1 when nothing answered within the timeout. */
    static int findConnectPort(Context c, long timeoutMs) {
        final AtomicInteger found = new AtomicInteger(-1);
        Discovery d = new Discovery(c.getApplicationContext(), found);
        d.start();
        try {
            long end = SystemClock.elapsedRealtime() + timeoutMs;
            while (SystemClock.elapsedRealtime() < end) {
                int p = found.get();
                if (p > 0) return p;
                p = readProperty();
                if (p > 0 && listening(p)) return p;
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }
            return found.get();
        } finally {
            d.stop();
        }
    }

    static boolean listening(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int readProperty() {
        try {
            Process p = new ProcessBuilder("getprop", "service.adb.tls.port").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                return line == null || line.trim().isEmpty() ? -1 : Integer.parseInt(line.trim());
            } finally {
                p.destroy();
            }
        } catch (Throwable t) {
            return -1;
        }
    }

    /** One resolve at a time: NsdManager (Android 11-13) refuses a second one while the first is out. */
    private static final class Discovery {
        private final NsdManager nsd;
        private final AtomicInteger found;
        private final ArrayDeque<NsdServiceInfo> queue = new ArrayDeque<>();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private boolean resolving;
        private volatile boolean stopped;
        private NsdManager.DiscoveryListener listener;

        Discovery(Context c, AtomicInteger found) {
            this.nsd = (NsdManager) c.getSystemService(Context.NSD_SERVICE);
            this.found = found;
        }

        void start() {
            listener = new NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String t) { }
                @Override public void onDiscoveryStopped(String t) { }
                @Override public void onStartDiscoveryFailed(String t, int e) { }
                @Override public void onStopDiscoveryFailed(String t, int e) { }
                @Override public void onServiceLost(NsdServiceInfo i) { }

                @Override
                public void onServiceFound(NsdServiceInfo info) {
                    synchronized (queue) {
                        queue.add(info);
                    }
                    next();
                }
            };
            try {
                nsd.discoverServices(TLS_CONNECT, NsdManager.PROTOCOL_DNS_SD, listener);
            } catch (Throwable ignored) {
            }
        }

        private void next() {
            final NsdServiceInfo info;
            synchronized (queue) {
                if (stopped || resolving || queue.isEmpty()) return;
                info = queue.poll();
                resolving = true;
            }
            try {
                nsd.resolveService(info, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo i, int err) {
                        done(i, false);
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo i) {
                        int p = i.getPort();
                        if (p > 0 && listening(p)) found.compareAndSet(-1, p);
                        done(i, true);
                    }
                });
            } catch (Throwable t) {
                done(info, false);
            }
        }

        private void done(NsdServiceInfo i, boolean ok) {
            synchronized (queue) {
                resolving = false;
            }
            handler.postDelayed(this::next, ok ? 0 : 300);
        }

        void stop() {
            stopped = true;
            try {
                if (listener != null) nsd.stopServiceDiscovery(listener);
            } catch (Throwable ignored) {
            }
        }
    }
}
