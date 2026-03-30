package com.example.cache.certificate.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CertificateControllerIntegrationTest {

    private static Path caDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Matcher<String> CERTIFICATE_BLOCK =
            Matchers.containsString("BEGIN CERTIFICATE");
    private static final Matcher<String> PRIVATE_KEY_BLOCK =
            Matchers.containsString("BEGIN PRIVATE KEY");

    @BeforeAll
    static void createCaDirectory() throws Exception {
        caDirectory = Files.createTempDirectory("ca-it-");
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("certificate.ca-directory", () -> caDirectory.toString());
        registry.add("certificate.ca-certificate-file", () -> "root-ca.crt.pem");
        registry.add("certificate.ca-private-key-file", () -> "root-ca.key.pem");
        registry.add("certificate.ca-private-key-password", () -> "changeit");
        registry.add("certificate.issued-certificate-ttl", () -> "PT1H");
        registry.add("certificate.trusted-service-ids", () -> "master,db-service,balancer");
        registry.add("server.ssl.enabled", () -> "false");
    }

    @BeforeEach
    void cleanDirectory() throws Exception {
        try (Stream<Path> pathStream = Files.list(caDirectory)) {
            pathStream.forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void shouldIssueCertificate() throws Exception {
        String payload = """
                {
                  "serviceId": "master",
                  "commonName": "master.internal",
                  "dnsNames": ["master.internal", "master"]
                }
                """;

        mockMvc.perform(post("/api/certificates/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificatePem").value(CERTIFICATE_BLOCK))
                .andExpect(jsonPath("$.privateKeyPem").value(PRIVATE_KEY_BLOCK))
                .andExpect(jsonPath("$.caCertificatePem").value(CERTIFICATE_BLOCK));
    }

    @Test
    void shouldRejectUntrustedService() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "serviceId", "unknown",
                "commonName", "unknown.internal",
                "dnsNames", java.util.List.of("unknown.internal")
        ));

        mockMvc.perform(post("/api/certificates/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNTRUSTED_SERVICE"));
    }

    @Test
    void shouldRenewCertificate() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "serviceId", "db-service",
                "commonName", "db.internal",
                "dnsNames", java.util.List.of("db.internal")
        ));

        mockMvc.perform(post("/api/certificates/renew")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificatePem").exists())
                .andExpect(jsonPath("$.notBefore").exists())
                .andExpect(jsonPath("$.notAfter").exists());
    }
}
