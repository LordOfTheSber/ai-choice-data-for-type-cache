package com.example.cache.balancer.tls;

import com.example.cache.balancer.config.TlsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Periodically contacts the external cert-service to check for certificate renewals.
 * Actual keystore hot-reload depends on the runtime TLS implementation (Jetty SslContextFactory).
 */
@Service
public class CertificateRefreshService {

    private static final Logger log = LoggerFactory.getLogger(CertificateRefreshService.class);

    private final TlsProperties tlsProperties;
    private final RestClient restClient;

    public CertificateRefreshService(TlsProperties tlsProperties, RestClient.Builder builder) {
        this.tlsProperties = tlsProperties;
        this.restClient = builder.build();
    }

    @Scheduled(fixedDelayString = "#{${tls.cert-service.refresh-interval-seconds:3600} * 1000}")
    public void refreshCertificates() {
        String url = tlsProperties.getBaseUrl() + "/api/certs/status";
        try {
            String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);
            log.info("Certificate status check completed: {}", response);
        } catch (RestClientException exception) {
            log.warn("Failed to reach cert-service at {}: {}", url, exception.getMessage());
        }
    }
}
