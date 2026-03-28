package com.example.cache.certificate.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cache.certificate.config.CertificateProperties;
import com.example.cache.certificate.model.CertificateRequest;
import com.example.cache.certificate.model.CertificateResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CertificateIssuanceConcurrencyTest {

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void shouldIssueCertificatesConcurrentlyWithoutFailures() throws Exception {
        CertificateIssuanceService issuanceService = createIssuanceService();
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        List<Callable<CertificateResponse>> tasks = buildTasks(issuanceService, 40);

        List<Future<CertificateResponse>> futures = executorService.invokeAll(tasks);
        executorService.shutdown();

        List<String> certificateBodies = new ArrayList<>();
        for (Future<CertificateResponse> future : futures) {
            CertificateResponse response = future.get();
            certificateBodies.add(response.certificatePem());
            assertThat(response.notAfter()).isAfter(response.notBefore());
        }
        assertThat(certificateBodies).doesNotHaveDuplicates();
    }

    private CertificateIssuanceService createIssuanceService() throws Exception {
        CertificateProperties properties = new CertificateProperties();
        Path caDirectory = Files.createTempDirectory("ca-concurrency-");
        properties.setCaDirectory(caDirectory);
        properties.setCaCertificateFile("root-ca.crt.pem");
        properties.setCaPrivateKeyFile("root-ca.key.pem");
        properties.setCaPrivateKeyPassword("changeit");
        properties.setIssuedCertificateTtl(Duration.ofHours(3));
        properties.setTrustedServiceIds(Set.of("master"));
        properties.setIssuedKeySize(2048);
        properties.setCaKeySize(2048);
        properties.setSignatureAlgorithm("SHA256withRSA");
        CertificateAuthorityService caService = new CertificateAuthorityService(properties);
        caService.initialize();
        return new CertificateIssuanceService(properties, caService, new PemEncodingService());
    }

    private List<Callable<CertificateResponse>> buildTasks(
            CertificateIssuanceService issuanceService,
            int taskCount
    ) {
        List<Callable<CertificateResponse>> tasks = new ArrayList<>(taskCount);
        for (int index = 0; index < taskCount; index++) {
            final String dnsName = "master-" + index + ".internal";
            tasks.add(() -> issuanceService.issueCertificate(
                    new CertificateRequest("master", dnsName, List.of(dnsName))
            ));
        }
        return tasks;
    }
}
