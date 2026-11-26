package dev.shizzi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/**
 * The app's adb identity (RSA key + self-signed certificate, generated once and kept in private
 * storage) on top of libadb-android. Everything that touches the library's API lives in this file
 * and AdbHelper, so a version difference is a one-file fix.
 */
final class AdbBridge extends AbsAdbConnectionManager {

    private static final String PREFS = "adb_identity";
    private static final String K_KEY = "key";
    private static final String K_CERT = "cert";
    private static final String K_PAIRED = "paired";
    private static final String K_TCP = "tcp_mode";
    private static final String K_TCP_PORT = "tcp_port";
    private static final String K_TCP_RANDOM = "tcp_random";
    private static final String K_TCP_ACTIVE = "tcp_active";
    static final int DEFAULT_TCP_PORT = 5555;
    private static final long TWENTY_YEARS_MS = 20L * 365 * 24 * 3600 * 1000;

    private static AdbBridge instance;

    private final PrivateKey privateKey;
    private final Certificate certificate;

    static synchronized AdbBridge get(Context c) throws Exception {
        if (instance == null) instance = new AdbBridge(c.getApplicationContext());
        return instance;
    }

    private AdbBridge(Context c) throws Exception {
        // Hidden-API checks are already lifted for the whole process by App (HiddenApiBypass "L"),
        // which libadb's same-device pairing needs (libadb-android#40).
        setApi(Build.VERSION.SDK_INT);

        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        PrivateKey k = null;
        Certificate cert = null;
        String ks = sp.getString(K_KEY, null);
        String cs = sp.getString(K_CERT, null);
        if (ks != null && cs != null) {
            try {
                k = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(ks, Base64.NO_WRAP)));
                cert = CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(Base64.decode(cs, Base64.NO_WRAP)));
            } catch (Exception broken) {
                k = null;
                cert = null;
            }
        }
        if (k == null || cert == null) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            long now = System.currentTimeMillis();
            byte[] der = AdbCert.selfSigned(kp, "Shizzi", now - 24L * 3600 * 1000, now + TWENTY_YEARS_MS);
            cert = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
            k = kp.getPrivate();
            // A new identity is a new, unpaired client.
            sp.edit()
                    .putString(K_KEY, Base64.encodeToString(k.getEncoded(), Base64.NO_WRAP))
                    .putString(K_CERT, Base64.encodeToString(der, Base64.NO_WRAP))
                    .putBoolean(K_PAIRED, false)
                    .apply();
        }
        privateKey = k;
        certificate = cert;
    }

    // public (not protected): widening is legal whichever visibility the library declares
    @Override
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    public Certificate getCertificate() {
        return certificate;
    }

    @Override
    public String getDeviceName() {
        return "Shizzi";
    }

    static boolean isPaired(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(K_PAIRED, false);
    }

    static void markPaired(Context c, boolean paired) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(K_PAIRED, paired).apply();
    }

    /** TCP mode (default on): keep adbd listening on a classic port so the helper restarts without Wi-Fi. */
    static boolean isTcpMode(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(K_TCP, true);
    }

    static void setTcpMode(Context c, boolean on) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(K_TCP, on).apply();
    }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean validPort(int port) {
        return port >= 1024 && port <= 65535;
    }

    /** The fixed port used when random is off. */
    static int tcpPort(Context c) {
        int p = prefs(c).getInt(K_TCP_PORT, DEFAULT_TCP_PORT);
        return validPort(p) ? p : DEFAULT_TCP_PORT;
    }

    static void setTcpPort(Context c, int port) {
        if (validPort(port)) prefs(c).edit().putInt(K_TCP_PORT, port).apply();
    }

    /** A new random port (1024-65535) is picked every time the port is opened. */
    static boolean isTcpRandom(Context c) {
        return prefs(c).getBoolean(K_TCP_RANDOM, false);
    }

    static void setTcpRandom(Context c, boolean on) {
        prefs(c).edit().putBoolean(K_TCP_RANDOM, on).apply();
    }

    /** The port adbd was last asked to open (0 = none); only meaningful until the next reboot. */
    static int tcpActivePort(Context c) {
        return prefs(c).getInt(K_TCP_ACTIVE, 0);
    }

    static void setTcpActivePort(Context c, int port) {
        prefs(c).edit().putInt(K_TCP_ACTIVE, port).apply();
    }
}
