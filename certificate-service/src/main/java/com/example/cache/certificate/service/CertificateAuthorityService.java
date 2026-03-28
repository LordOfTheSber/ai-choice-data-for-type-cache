package com.example.cache.certificate.service;

import com.example.cache.certificate.config.CertificateProperties;
import com.example.cache.certificate.exception.CertificateErrorCode;
import com.example.cache.certificate.exception.CertificateServiceException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.openssl.MiscPEMGenerator;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMEncryptor;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateAuthorityService {

    private static final String BC_PROVIDER = "BC";
    private final CertificateProperties certificateProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile CaMaterial caMaterial;

    @PostConstruct
    public void initialize() {
        lock.writeLock().lock();
        try {
            caMaterial = loadOrCreateCa();
            log.info("Certificate Authority is ready. Subject={}", caMaterial.certificate().getSubjectX500Principal());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CaMaterial getCaMaterial() {
        lock.readLock().lock();
        try {
            if (caMaterial == null) {
                throw new CertificateServiceException(
                        CertificateErrorCode.CA_INITIALIZATION_FAILED,
                        "Certificate Authority is not initialized"
                );
            }
            return caMaterial;
        } finally {
            lock.readLock().unlock();
        }
    }

    private CaMaterial loadOrCreateCa() {
        try {
            prepareDirectory();
            if (caFilesExist()) {
                return readCaMaterial();
            }
            CaMaterial generatedMaterial = generateCaMaterial();
            persistCaMaterial(generatedMaterial);
            return generatedMaterial;
        } catch (IOException | GeneralSecurityException | OperatorCreationException exception) {
            throw new CertificateServiceException(
                    CertificateErrorCode.CA_INITIALIZATION_FAILED,
                    "Failed to initialize Certificate Authority",
                    exception
            );
        }
    }

    private void prepareDirectory() throws IOException {
        Files.createDirectories(certificateProperties.getCaDirectory());
    }

    private boolean caFilesExist() {
        return Files.exists(certificateProperties.caCertificatePath())
                && Files.exists(certificateProperties.caPrivateKeyPath());
    }

    private CaMaterial readCaMaterial() throws IOException, GeneralSecurityException, OperatorCreationException {
        X509Certificate certificate = readCertificate(certificateProperties.caCertificatePath());
        PrivateKey privateKey = readPrivateKey(certificateProperties.caPrivateKeyPath());
        return new CaMaterial(certificate, privateKey);
    }

    private CaMaterial generateCaMaterial() throws GeneralSecurityException, OperatorCreationException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(certificateProperties.getCaKeySize(), secureRandom);
        KeyPair keyPair = generator.generateKeyPair();
        X509Certificate certificate = selfSignedCertificate(keyPair);
        return new CaMaterial(certificate, keyPair.getPrivate());
    }

    private X509Certificate selfSignedCertificate(KeyPair keyPair)
            throws OperatorCreationException, GeneralSecurityException {
        Instant notBefore = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant notAfter = Instant.now().plus(3650, ChronoUnit.DAYS);
        BigInteger serialNumber = new BigInteger(160, secureRandom).abs();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new org.bouncycastle.asn1.x500.X500Name("CN=Internal Root CA"),
                serialNumber,
                Date.from(notBefore),
                Date.from(notAfter),
                new org.bouncycastle.asn1.x500.X500Name("CN=Internal Root CA"),
                keyPair.getPublic()
        );
        ContentSigner signer = new JcaContentSignerBuilder(certificateProperties.getSignatureAlgorithm())
                .setProvider(BC_PROVIDER)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().setProvider(BC_PROVIDER).getCertificate(holder);
    }

    private void persistCaMaterial(CaMaterial material) throws IOException, OperatorCreationException {
        writeCertificate(certificateProperties.caCertificatePath(), material.certificate());
        writeEncryptedPrivateKey(certificateProperties.caPrivateKeyPath(), material.privateKey());
    }

    private X509Certificate readCertificate(Path certificatePath) throws IOException, GeneralSecurityException {
        try (Reader reader = Files.newBufferedReader(certificatePath); PEMParser parser = new PEMParser(reader)) {
            Object value = parser.readObject();
            X509CertificateHolder certificateHolder = (X509CertificateHolder) value;
            return new JcaX509CertificateConverter().setProvider(BC_PROVIDER).getCertificate(certificateHolder);
        }
    }

    private PrivateKey readPrivateKey(Path privateKeyPath) throws IOException, OperatorCreationException {
        try (Reader reader = Files.newBufferedReader(privateKeyPath); PEMParser parser = new PEMParser(reader)) {
            Object keyObject = parser.readObject();
            return resolvePrivateKey(keyObject);
        }
    }

    private PrivateKey resolvePrivateKey(Object keyObject) throws OperatorCreationException {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BC_PROVIDER);
        if (keyObject instanceof PEMEncryptedKeyPair encryptedKeyPair) {
            return decryptPrivateKey(converter, encryptedKeyPair);
        }
        if (keyObject instanceof PEMKeyPair pemKeyPair) {
            return converter.getKeyPair(pemKeyPair).getPrivate();
        }
        throw new CertificateServiceException(
                CertificateErrorCode.CA_INITIALIZATION_FAILED,
                "Unsupported private key format"
        );
    }

    private PrivateKey decryptPrivateKey(
            JcaPEMKeyConverter converter,
            PEMEncryptedKeyPair encryptedKeyPair
    ) throws OperatorCreationException {
        PEMDecryptorProvider decryptorProvider = new JcePEMDecryptorProviderBuilder()
                .build(certificateProperties.getCaPrivateKeyPasswordChars());
        PEMKeyPair keyPair = encryptedKeyPair.decryptKeyPair(decryptorProvider);
        return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
    }

    private void writeCertificate(Path outputPath, X509Certificate certificate) throws IOException {
        try (Writer writer = Files.newBufferedWriter(outputPath); JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(certificate);
            pemWriter.flush();
        }
    }

    private void writeEncryptedPrivateKey(Path outputPath, PrivateKey privateKey)
            throws IOException, OperatorCreationException {
        try (Writer writer = Files.newBufferedWriter(outputPath); JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            MiscPEMGenerator generator = encryptedKeyGenerator(privateKey);
            pemWriter.writeObject(generator);
            pemWriter.flush();
        }
    }

    private MiscPEMGenerator encryptedKeyGenerator(PrivateKey privateKey) throws OperatorCreationException {
        PEMEncryptor encryptor = new JcePEMEncryptorBuilder("AES-256-CBC")
                .setProvider(BC_PROVIDER)
                .setSecureRandom(secureRandom)
                .build(certificateProperties.getCaPrivateKeyPasswordChars());
        return new MiscPEMGenerator(privateKey, encryptor);
    }
}
