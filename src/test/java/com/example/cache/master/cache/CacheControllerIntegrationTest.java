package com.example.cache.master.cache;

import com.example.cache.master.cache.api.GlobalExceptionHandler;
import com.example.cache.master.cache.serialization.dto.BookMetadataDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.example.cache.master.cache.api.CacheController.class)
@Import(GlobalExceptionHandler.class)
class CacheControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MasterCacheService masterCacheService;

    @Test
    void putGetDeleteFlowShouldWorkThroughRestApi() throws Exception {
        CacheValue cacheValue = new CacheValue(
            "payload",
            DataClass.IMMUTABLE,
            Duration.ofMillis(120_000),
            CacheStatus.HIT,
            5
        );
        when(masterCacheService.get("book-1")).thenReturn(Optional.of(cacheValue));
        doNothing().when(masterCacheService).put(any(), any());
        doNothing().when(masterCacheService).delete("book-1");

        String body = """
            {
              "payload":"payload",
              "dataClass":"IMMUTABLE",
              "ttlMillis":120000,
              "version":5
            }
            """;

        mockMvc.perform(put("/api/cache/book-1").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/cache/book-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload").value("payload"))
            .andExpect(jsonPath("$.version").value(5));

        mockMvc.perform(delete("/api/cache/book-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void typedPutAndGetShouldSupportConsumerDto() throws Exception {
        BookMetadataDto dto = new BookMetadataDto("Author", "ru", 2020, "isbn");
        CacheValue value = new CacheValue(dto, DataClass.IMMUTABLE, Duration.ofMinutes(3), CacheStatus.HIT, 9);

        when(masterCacheService.get("book-meta")).thenReturn(Optional.of(value));
        doNothing().when(masterCacheService).put(any(), any());

        String body = """
            {
              "typeName":"com.example.cache.master.cache.serialization.dto.BookMetadataDto",
              "payload":{"author":"Author","language":"ru","publicationYear":2020,"isbn":"isbn"},
              "dataClass":"IMMUTABLE",
              "ttlMillis":180000,
              "version":9
            }
            """;

        mockMvc.perform(put("/api/cache/book-meta/typed").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/cache/book-meta/typed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.typeName").value(BookMetadataDto.class.getName()))
            .andExpect(jsonPath("$.payload.author").value("Author"))
            .andExpect(jsonPath("$.version").value(9));
    }

    @Test
    void invalidTypedRequestShouldReturnBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(new InvalidTypedRequest("", null, "IMMUTABLE", 0, -1));

        mockMvc.perform(put("/api/cache/invalid/typed").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRequestShouldReturnBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(new InvalidRequest("", "IMMUTABLE", 0, -1));

        mockMvc.perform(put("/api/cache/invalid").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    private record InvalidRequest(String payload, String dataClass, long ttlMillis, long version) {
    }

    private record InvalidTypedRequest(String typeName, Object payload, String dataClass, long ttlMillis, long version) {
    }
}
