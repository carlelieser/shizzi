package dev.shizzi;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Minimal self-signed X.509 v3 certificate (RSA, SHA256withRSA) written as DER by hand.
 *
 * adbd's TLS pairing / connect only needs the client to present a certificate for the RSA key it
 * paired with; the platform has no public API to build one and a bundled BouncyCastle clashes with
 * the stripped copy in the boot classpath, so the few ASN.1 structures needed are encoded here.
 */
final class AdbCert {

    private AdbCert() {
    }

    static byte[] selfSigned(KeyPair kp, String commonName, long notBeforeMs, long notAfterMs)
            throws GeneralSecurityException {
        byte[] sigAlg = seq(oid(new int[]{1, 2, 840, 113549, 1, 1, 11}), new byte[]{0x05, 0x00});

        byte[] serial = new byte[16];
        new SecureRandom().nextBytes(serial);
        serial[0] = (byte) ((serial[0] & 0x7f) | 0x01);           // positive, minimal DER INTEGER

        byte[] name = seq(set(seq(oid(new int[]{2, 5, 4, 3}), tlv(0x0c, commonName.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        byte[] validity = seq(time(notBeforeMs), time(notAfterMs));

        byte[] tbs = seq(
                tlv(0xa0, tlv(0x02, new byte[]{0x02})),            // [0] version v3
                tlv(0x02, serial),
                sigAlg,
                name,                                              // issuer
                validity,
                name,                                              // subject
                kp.getPublic().getEncoded());                      // SubjectPublicKeyInfo (X.509 DER)

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(tbs);
        byte[] signature = sig.sign();

        byte[] bits = new byte[signature.length + 1];              // BIT STRING: 0 unused bits
        System.arraycopy(signature, 0, bits, 1, signature.length);
        return seq(tbs, sigAlg, tlv(0x03, bits));
    }

    // ------------------------------------------------------------------ DER

    private static byte[] time(long ms) {
        Date d = new Date(ms);
        boolean general = ms >= 2524608000000L;                    // 2050-01-01: UTCTime ends at 2049
        SimpleDateFormat f = new SimpleDateFormat(general ? "yyyyMMddHHmmss'Z'" : "yyMMddHHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(general ? 0x18 : 0x17, f.format(d).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static byte[] oid(int[] c) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(c[0] * 40 + c[1]);
        for (int i = 2; i < c.length; i++) {
            int v = c[i];
            int n = 1;
            for (int t = v >> 7; t > 0; t >>= 7) n++;
            for (int k = n - 1; k >= 0; k--) {
                int part = (v >> (7 * k)) & 0x7f;
                o.write(k > 0 ? (part | 0x80) : part);
            }
        }
        return tlv(0x06, o.toByteArray());
    }

    private static byte[] seq(byte[]... parts) {
        return tlv(0x30, cat(parts));
    }

    private static byte[] set(byte[]... parts) {
        return tlv(0x31, cat(parts));
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    private static byte[] tlv(int tag, byte[] content) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        int len = content.length;
        if (len < 0x80) {
            o.write(len);
        } else if (len < 0x100) {
            o.write(0x81);
            o.write(len);
        } else if (len < 0x10000) {
            o.write(0x82);
            o.write(len >> 8);
            o.write(len & 0xff);
        } else {
            o.write(0x83);
            o.write(len >> 16);
            o.write((len >> 8) & 0xff);
            o.write(len & 0xff);
        }
        o.write(content, 0, content.length);
        return o.toByteArray();
    }
}
