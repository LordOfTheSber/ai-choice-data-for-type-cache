package com.example.cache.master.cache;

import com.example.cache.master.cache.api.CacheGetResponse;
import com.example.cache.master.cache.api.CachePutRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "cache.l3.enabled=true",
        "cache.l3.path=./build/cache-it-l3",
        "cache.hot-region.promotion-threshold=3"
    }
)
class CacheControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void putGetDeleteFlowShouldWorkThroughRestApi() {
        CachePutRequest request = new CachePutRequest("payload", DataClass.IMMUTABLE, 120_000, 5);

        ResponseEntity<Void> putResponse = restTemplate.exchange(
            "/api/cache/book-1",
            HttpMethod.PUT,
            new HttpEntity<>(request),
            Void.class
        );
        assertEquals(HttpStatus.ACCEPTED, putResponse.getStatusCode());

        ResponseEntity<CacheGetResponse> getResponse = restTemplate.getForEntity(
            "/api/cache/book-1",
            CacheGetResponse.class
        );
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());
        assertEquals("payload", getResponse.getBody().payload());
        assertEquals(5L, getResponse.getBody().version());

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            "/api/cache/book-1",
            HttpMethod.DELETE,
            null,
            Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        ResponseEntity<CacheGetResponse> missResponse = restTemplate.getForEntity(
            "/api/cache/book-1",
            CacheGetResponse.class
        );
        assertEquals(HttpStatus.NOT_FOUND, missResponse.getStatusCode());
    }

    @Test
    void invalidRequestShouldReturnBadRequest() {
        CachePutRequest request = new CachePutRequest("", DataClass.IMMUTABLE, 0, -1);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/cache/invalid",
            HttpMethod.PUT,
            new HttpEntity<>(request),
            String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
