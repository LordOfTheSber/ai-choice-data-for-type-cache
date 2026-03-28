package com.example.cache.balancer.client;

import com.example.cache.balancer.config.MasterClientProperties;
import com.example.cache.balancer.error.RouterErrorCode;
import com.example.cache.balancer.error.RouterException;
import com.example.cache.balancer.registry.MasterNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MasterHttpClient {

    private static final Logger log = LoggerFactory.getLogger(MasterHttpClient.class);
    private static final String CACHE_PATH = "/api/cache/";

    private final RestClient restClient;
    private final MasterClientProperties properties;
    private final Timer requestTimer;

    public MasterHttpClient(RestClient masterRestClient,
                            MasterClientProperties properties,
                            MeterRegistry meterRegistry) {
        this.restClient = masterRestClient;
        this.properties = properties;
        this.requestTimer = meterRegistry.timer("router.master.request.duration");
    }

    public MasterResponse get(MasterNode node, String key) {
        return executeWithRetry(node, HttpMethod.GET, key, null);
    }

    public MasterResponse put(MasterNode node, String key, String payload) {
        return executeWithRetry(node, HttpMethod.PUT, key, payload);
    }

    public MasterResponse delete(MasterNode node, String key) {
        return executeWithRetry(node, HttpMethod.DELETE, key, null);
    }

    private MasterResponse executeWithRetry(MasterNode node, HttpMethod method,
                                            String key, String body) {
        int maxAttempts = properties.getRetry().getMaxAttempts();
        long delayMillis = properties.getRetry().getDelayMillis();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return timedExecute(node, method, key, body);
            } catch (RestClientException exception) {
                lastException = exception;
                log.warn("Master call failed node={} method={} key={} attempt={}/{}",
                    node.nodeId(), method, key, attempt, maxAttempts, exception);
                sleepBeforeRetry(attempt, maxAttempts, delayMillis);
            }
        }

        throw new RouterException(
            RouterErrorCode.MASTER_COMMUNICATION_ERROR,
            "Failed to reach master node=" + node.nodeId() + " after " + maxAttempts + " attempts",
            lastException
        );
    }

    private MasterResponse timedExecute(MasterNode node, HttpMethod method,
                                        String key, String body) {
        return requestTimer.record(() -> execute(node, method, key, body));
    }

    private MasterResponse execute(MasterNode node, HttpMethod method, String key, String body) {
        String url = node.baseUrl() + CACHE_PATH + key;

        RestClient.RequestBodySpec spec = restClient.method(method)
            .uri(url)
            .accept(MediaType.APPLICATION_JSON);

        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return spec.exchange((request, response) -> {
            String responseBody = new String(response.getBody().readAllBytes());
            return new MasterResponse(response.getStatusCode(), responseBody);
        });
    }

    private void sleepBeforeRetry(int attempt, int maxAttempts, long baseDelayMillis) {
        if (attempt >= maxAttempts) {
            return;
        }
        try {
            long delay = baseDelayMillis * attempt;
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RouterException(RouterErrorCode.INTERNAL_ERROR, "Retry interrupted");
        }
    }
}
