package com.example.cache.master.cache;

import com.example.cache.master.cache.api.GlobalExceptionHandler;
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
    void invalidRequestShouldReturnBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(new InvalidRequest("", "IMMUTABLE", 0, -1));

        mockMvc.perform(put("/api/cache/invalid").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    private record InvalidRequest(String payload, String dataClass, long ttlMillis, long version) {
    }
}
