package dev.tvtimer.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;

final class AdbConnectionManager extends AbsAdbConnectionManager {
    private static final String PREFS = "adb_host_identity";
    private static final String PRIVATE_KEY = "private_key";
    private static final String CERTIFICATE = "certificate";
    private static volatile AdbConnectionManager instance;

    private final SharedPreferences preferences;
    private PrivateKey privateKey;
    private Certificate certificate;

    static AdbConnectionManager get(Context context) {
        AdbConnectionManager local = instance;
        if (local == null) {
            synchronized (AdbConnectionManager.class) {
                local = instance;
                if (local == null) {
                    local = new AdbConnectionManager(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    private AdbConnectionManager(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setApi(Build.VERSION.SDK_INT);
        setTimeout(15, TimeUnit.SECONDS);
        loadOrCreateIdentity();
    }

    private void loadOrCreateIdentity() {
        String encodedKey = preferences.getString(PRIVATE_KEY, null);
        String encodedCertificate = preferences.getString(CERTIFICATE, null);
        if (encodedKey != null && encodedCertificate != null) {
            try {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(
                        Base64.decode(encodedKey, Base64.NO_WRAP)));
                CertificateFactory certificates = CertificateFactory.getInstance("X.509");
                certificate = certificates.generateCertificate(
                        new java.io.ByteArrayInputStream(Base64.decode(encodedCertificate, Base64.NO_WRAP)));
                return;
            } catch (Exception ignored) {
                preferences.edit().clear().commit();
            }
        }
        createIdentity();
    }

    private void createIdentity() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
            java.security.KeyPair keyPair = generator.generateKeyPair();
            privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();

            String algorithm = "SHA512withRSA";
            Date notBefore = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
            Date notAfter = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3650));
            X500Name owner = new X500Name("CN=Android Screen Timer Parent");

            CertificateExtensions extensions = new CertificateExtensions();
            extensions.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(
                    new KeyIdentifier(publicKey).getIdentifier()));
            extensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));

            X509CertInfo info = new X509CertInfo();
            info.set("version", new CertificateVersion(2));
            info.set("serialNumber", new CertificateSerialNumber(
                    new Random().nextInt() & Integer.MAX_VALUE));
            info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithm)));
            info.set("subject", new CertificateSubjectName(owner));
            info.set("issuer", new CertificateIssuerName(owner));
            info.set("key", new CertificateX509Key(publicKey));
            info.set("validity", new CertificateValidity(notBefore, notAfter));
            info.set("extensions", extensions);

            X509CertImpl generated = new X509CertImpl(info);
            generated.sign(privateKey, algorithm);
            certificate = generated;

            boolean saved = preferences.edit()
                    .putString(PRIVATE_KEY, Base64.encodeToString(privateKey.getEncoded(), Base64.NO_WRAP))
                    .putString(CERTIFICATE, Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP))
                    .commit();
            if (!saved) {
                throw new IllegalStateException("Could not persist ADB identity");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create the local ADB identity", exception);
        }
    }

    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @Override
    protected String getDeviceName() {
        return "Android Screen Timer Parent";
    }
}
