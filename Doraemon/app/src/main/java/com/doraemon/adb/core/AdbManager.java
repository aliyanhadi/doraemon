package com.doraemon.adb.core;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

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
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SubjectKeyIdentifierExtension;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

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

    private static Certificate generateCert(PublicKey pub, PrivateKey priv) throws Exception {
        String algo = "SHA512withRSA";
        CertificateExtensions ext = new CertificateExtensions();
        ext.set(SubjectKeyIdentifierExtension.NAME,
                new SubjectKeyIdentifierExtension(new KeyIdentifier(pub).getIdentifier()));
        X500Name name = new X500Name("CN=Doraemon");
        Date from = new Date();
        Date to = new Date(from.getTime() + 10L * 365 * 24 * 3600 * 1000);
        X509CertInfo info = new X509CertInfo();
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(AlgorithmId.get(algo)));
        info.set(X509CertInfo.SUBJECT, name);
        info.set(X509CertInfo.KEY, new CertificateX509Key(pub));
        info.set(X509CertInfo.VALIDITY, new CertificateValidity(from, to));
        info.set(X509CertInfo.ISSUER, name);
        info.set(X509CertInfo.EXTENSIONS, ext);
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(priv, algo);
        return cert;
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
