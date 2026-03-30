package com.example.cache.certificate.support;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class TestCertificateFactory {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TestCertificateFactory() {
    }

    public static KeyPair generateRsaKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize, SECURE_RANDOM);
        return generator.generateKeyPair();
    }

    public static X509Certificate selfSigned(KeyPair keyPair, String commonName) throws Exception {
        Instant notBefore = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant notAfter = Instant.now().plus(365, ChronoUnit.DAYS);
        BigInteger serialNumber = new BigInteger(128, SECURE_RANDOM).abs();
        X500Name subject = new X500Name("CN=" + commonName);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                serialNumber,
                Date.from(notBefore),
                Date.from(notAfter),
                subject,
                keyPair.getPublic()
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }
}
