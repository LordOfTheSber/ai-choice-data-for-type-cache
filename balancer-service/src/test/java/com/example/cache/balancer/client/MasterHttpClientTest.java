package com.example.cache.balancer.client;

import com.example.cache.balancer.config.MasterClientProperties;
import com.example.cache.balancer.error.RouterException;
import com.example.cache.balancer.registry.MasterNode;
import com.example.cache.balancer.registry.MasterNodeStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MasterHttpClientTest {

    private MockRestServiceServer mockServer;
    private MasterHttpClient client;
    private MasterNode testNode;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        MasterClientProperties props = new MasterClientProperties();
        props.getRetry().setMaxAttempts(1);
        props.getRetry().setDelayMillis(10);

        client = new MasterHttpClient(restClient, props, new SimpleMeterRegistry());
        testNode = new MasterNode("node-1", "https://node-1:8443", 1,
            MasterNodeStatus.UP, Instant.now());
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void get_successfulResponse_returnsMasterResponse() {
        mockServer.expect(requestTo("https://node-1:8443/api/cache/my-key"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"value\":\"hello\"}", MediaType.APPLICATION_JSON));

        MasterResponse response = client.get(testNode, "my-key");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.body()).contains("hello");
    }

    @Test
    void get_notFoundResponse_returnsNotFound() {
        mockServer.expect(requestTo("https://node-1:8443/api/cache/missing-key"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND).body("not found"));

        MasterResponse response = client.get(testNode, "missing-key");

        assertThat(response.isNotFound()).isTrue();
    }

    @Test
    void put_successfulResponse_returns202() {
        mockServer.expect(requestTo("https://node-1:8443/api/cache/put-key"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.ACCEPTED));

        MasterResponse response = client.put(testNode, "put-key", "{\"payload\":\"val\"}");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.status().value()).isEqualTo(202);
    }

    @Test
    void delete_successfulResponse_returns204() {
        mockServer.expect(requestTo("https://node-1:8443/api/cache/del-key"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        MasterResponse response = client.delete(testNode, "del-key");

        assertThat(response.status().value()).isEqualTo(204);
    }

    @Test
    void get_retryExhausted_throwsRouterException() {
        MasterClientProperties retryProps = new MasterClientProperties();
        retryProps.getRetry().setMaxAttempts(2);
        retryProps.getRetry().setDelayMillis(10);

        RestClient.Builder retryBuilder = RestClient.builder();
        MockRestServiceServer retryMockServer = MockRestServiceServer.bindTo(retryBuilder)
            .build();
        RestClient retryClient = retryBuilder.build();

        MasterHttpClient retryHttpClient = new MasterHttpClient(
            retryClient, retryProps, new SimpleMeterRegistry()
        );

        retryMockServer.expect(requestTo("https://node-1:8443/api/cache/fail-key"))
            .andRespond(request -> {
                throw new org.springframework.web.client.ResourceAccessException("Connection refused");
            });
        retryMockServer.expect(requestTo("https://node-1:8443/api/cache/fail-key"))
            .andRespond(request -> {
                throw new org.springframework.web.client.ResourceAccessException("Connection refused");
            });

        assertThatThrownBy(() -> retryHttpClient.get(testNode, "fail-key"))
            .isInstanceOf(RouterException.class)
            .hasMessageContaining("Failed to reach master");
    }
}
