package com.example.cache.master.cache;

import com.example.cache.master.cache.api.GlobalExceptionHandler;
import com.example.cache.master.cache.serialization.BinarySerializationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @MockBean
    private BinarySerializationService binarySerializationService;

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
    void binaryPutAndGetShouldUseKryoPayload() throws Exception {
        byte[] requestPayload = new byte[]{1, 2, 3};
        byte[] responsePayload = new byte[]{9, 8, 7};
        CacheValue cacheValue = new CacheValue("binary-value", DataClass.IMMUTABLE, Duration.ofSeconds(30), CacheStatus.HIT, 11);

        when(binarySerializationService.deserialize(requestPayload, String.class)).thenReturn("binary-value");
        when(masterCacheService.get("book-bin")).thenReturn(Optional.of(cacheValue));
        when(binarySerializationService.serialize("binary-value")).thenReturn(responsePayload);
        doNothing().when(masterCacheService).put(any(), any());

        mockMvc.perform(put("/api/cache/book-bin/binary")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Payload-Type", String.class.getName())
                .header("X-Data-Class", "IMMUTABLE")
                .header("X-Ttl-Millis", "30000")
                .header("X-Version", "11")
                .content(requestPayload))
            .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/cache/book-bin/binary").accept(MediaType.APPLICATION_OCTET_STREAM))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Payload-Type", String.class.getName()))
            .andExpect(header().string("X-Data-Class", "IMMUTABLE"))
            .andExpect(header().string("X-Version", "11"))
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertArrayEquals(
                responsePayload,
                result.getResponse().getContentAsByteArray()
            ));
    }

    @Test
    void binaryPutShouldReturnBadRequestForInvalidHeaders() throws Exception {
        mockMvc.perform(put("/api/cache/book-bin/binary")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Payload-Type", "")
                .header("X-Data-Class", "IMMUTABLE")
                .header("X-Ttl-Millis", "0")
                .header("X-Version", "-1")
                .content(new byte[]{1}))
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
}
