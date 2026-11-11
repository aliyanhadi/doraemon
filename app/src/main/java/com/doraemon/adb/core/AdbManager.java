package com.doraemon.adb.core;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;


/** Singleton ADB connection manager. Keys are generated once and persisted in app-private storage. */
public final class AdbManager extends AbsAdbConnectionManager {
    private static AdbManager instance;
    private final PrivateKey privateKey;
    private final Certificate certificate;

    public static synchronized AdbManager get(Context ctx) {
        if (instance == null) {
            try {
                instance = new AdbManager(ctx.getApplicationContext());
            } catch (Exception e) {
                throw new IllegalStateException("Cannot init ADB keys", e);
            }
        }
        return instance;
    }

    private AdbManager(Context ctx) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        File keyFile = new File(ctx.getFilesDir(), "adb_key.pk8");
        File certFile = new File(ctx.getFilesDir(), "adb_cert.der");
        PrivateKey key;
        Certificate cert;
        if (keyFile.exists() && certFile.exists()) {
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(readAll(keyFile)));
            try (InputStream in = new FileInputStream(certFile)) {
                cert = CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
        } else {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            key = pair.getPrivate();
            cert = generateCert(pair.getPublic(), key);
            write(keyFile, key.getEncoded());
            write(certFile, cert.getEncoded());
        }
        this.privateKey = key;
        this.certificate = cert;
    }

    /** Self-signed X.509 v3 certificate built by hand (DER), so no extra crypto library is needed. */
    private static Certificate generateCert(PublicKey pub, PrivateKey priv) throws Exception {
        byte[] sigAlg = der(0x30, new byte[]{0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B},
                new byte[]{0x05, 0x00});                                  // sha256WithRSAEncryption
        byte[] name = der(0x30, der(0x31, der(0x30, new byte[]{0x06, 0x03, 0x55, 0x04, 0x03},
                der(0x0C, "Doraemon".getBytes("UTF-8")))));               // CN=Doraemon
        SimpleDateFormat fmt = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date from = new Date(System.currentTimeMillis() - 24L * 3600 * 1000);
        Date to = new Date(from.getTime() + 10L * 365 * 24 * 3600 * 1000);
        byte[] validity = der(0x30, der(0x17, fmt.format(from).getBytes("US-ASCII")),
                der(0x17, fmt.format(to).getBytes("US-ASCII")));
        byte[] serial = new BigInteger(63, new SecureRandom()).add(BigInteger.ONE).toByteArray();
        byte[] tbs = der(0x30,
                der(0xA0, der(0x02, new byte[]{0x02})),                   // version v3
                der(0x02, serial), sigAlg, name, validity, name, pub.getEncoded());
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(priv);
        sig.update(tbs);
        byte[] sigBytes = sig.sign();
        byte[] bitString = new byte[sigBytes.length + 1];
        System.arraycopy(sigBytes, 0, bitString, 1, sigBytes.length);
        byte[] cert = der(0x30, tbs, sigAlg, der(0x03, bitString));
        return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert));
    }

    private static byte[] der(int tag, byte[]... parts) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] p : parts) body.write(p);
        byte[] b = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (b.length < 128) {
            out.write(b.length);
        } else if (b.length < 256) {
            out.write(0x81);
            out.write(b.length);
        } else {
            out.write(0x82);
            out.write(b.length >> 8);
            out.write(b.length & 0xFF);
        }
        out.write(b);
        return out.toByteArray();
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void write(File f, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
    }

    @NonNull @Override public PrivateKey getPrivateKey() { return privateKey; }
    @NonNull @Override public Certificate getCertificate() { return certificate; }
    @NonNull @Override public String getDeviceName() { return "Doraemon"; }
}
