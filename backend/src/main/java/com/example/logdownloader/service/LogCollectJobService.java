package com.example.logdownloader.service;

import com.example.logdownloader.dto.DownloadRequest;
import com.example.logdownloader.dto.LogCollectStartResponse;
import com.example.logdownloader.dto.LogCollectStatusResponse;
import com.example.logdownloader.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class LogCollectJobService {

    private final LogStreamingService logStreamingService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

    public LogCollectJobService(LogStreamingService logStreamingService) {
        this.logStreamingService = logStreamingService;
    }

    public LogCollectStartResponse start(DownloadRequest request) {
        String id = UUID.randomUUID().toString();
        Path out = Path.of("data", "jobs", id + ".zip");
        JobState state = new JobState(id, "RUNNING", "Started", out, Instant.now(), null);
        jobs.put(id, state);

        executor.submit(() -> runJob(id, request));
        return new LogCollectStartResponse(id, "RUNNING");
    }

    public LogCollectStatusResponse status(String id) {
        JobState state = jobs.get(id);
        if (state == null) throw new ApiException(404, "Job not found: " + id);
        long size = 0L;
        try {
            if (Files.exists(state.output())) size = Files.size(state.output());
        } catch (IOException ignored) {
        }
        return new LogCollectStatusResponse(id, state.status(), state.message(), size);
    }

    public StreamingResponseBody download(String id) {
        JobState state = jobs.get(id);
        if (state == null) throw new ApiException(404, "Job not found: " + id);
        if (!"DONE".equals(state.status())) throw new ApiException(409, "Job is not completed: " + state.status());
        if (!Files.exists(state.output())) throw new ApiException(404, "Job file not found");

        return out -> {
            try (FileInputStream in = new FileInputStream(state.output().toFile())) {
                in.transferTo(out);
            }
        };
    }

    private void runJob(String id, DownloadRequest request) {
        JobState curr = jobs.get(id);
        try {
            Files.createDirectories(curr.output().getParent());
            StreamingResponseBody body = logStreamingService.download(request);
            try (FileOutputStream fos = new FileOutputStream(curr.output().toFile())) {
                body.writeTo(fos);
            }
            jobs.put(id, curr.withStatus("DONE", "Completed", null));
        } catch (Exception e) {
            jobs.put(id, curr.withStatus("FAILED", e.getMessage(), e));
        }
    }

    private record JobState(String id, String status, String message, Path output, Instant createdAt, Throwable error) {
        JobState withStatus(String status, String message, Throwable error) {
            return new JobState(id, status, message, output, createdAt, error);
        }
    }
}
