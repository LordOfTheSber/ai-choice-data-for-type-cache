package com.example.logdownloader.service;

import com.example.logdownloader.dto.DownloadRequest;
import com.example.logdownloader.dto.LogCollectStartResponse;
import com.example.logdownloader.dto.LogCollectStatusResponse;
import com.example.logdownloader.error.ApiException;
import com.example.logdownloader.util.LogTimeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LogCollectJobService {

    private static final Logger log = LoggerFactory.getLogger(LogCollectJobService.class);

    private final LogStreamingService logStreamingService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

    public LogCollectJobService(LogStreamingService logStreamingService) {
        this.logStreamingService = logStreamingService;
    }

    public LogCollectStartResponse start(DownloadRequest request) {
        String id = UUID.randomUUID().toString();
        Path out = Path.of("data", "jobs", id + ".zip");

        Instant from = null;
        try {
            from = LogTimeFilter.parseUserDateTime(request.from());
        } catch (Exception ignored) {
        }
        String initialStatus = (from != null && from.isAfter(Instant.now())) ? "SCHEDULED" : "RUNNING";

        JobState state = new JobState(id, initialStatus, "Started", out, Instant.now(), null);
        jobs.put(id, state);
        log.info("Start collect job id={} output={} status={} from={} to={} pollIntervalSeconds={} maxBytes={}",
                id,
                out,
                initialStatus,
                request.from(),
                request.to(),
                request.pollIntervalSeconds(),
                request.maxBytes());

        executor.submit(() -> runJob(id, request));
        return new LogCollectStartResponse(id, initialStatus);
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

            Instant from = null;
            try {
                from = LogTimeFilter.parseUserDateTime(request.from());
            } catch (Exception ignored) {
            }
            if (from != null && from.isAfter(Instant.now())) {
                jobs.put(id, curr.withStatus("SCHEDULED", "Waiting for start time", null));
                waitUntil(from);
            }

            jobs.put(id, curr.withStatus("RUNNING", "Collecting logs", null));
            StreamingResponseBody body = logStreamingService.downloadForCollect(request);
            try (FileOutputStream fos = new FileOutputStream(curr.output().toFile())) {
                body.writeTo(fos);
                fos.flush();
            }

            jobs.put(id, curr.withStatus("FINALIZING", "Finalizing archive", null));
            jobs.put(id, curr.withStatus("DONE", "Completed", null));
            log.info("Collect job DONE id={} output={}", id, curr.output());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            long size = 0L;
            try {
                if (Files.exists(curr.output())) size = Files.size(curr.output());
            } catch (IOException ignored) {
            }
            if (msg.toLowerCase().contains("stream closed") && size > 0) {
                jobs.put(id, curr.withStatus("DONE", "Completed with warning: stream closed after writing partial archive", null));
                log.warn("Collect job recovered as DONE id={} warning='{}' size={}", id, msg, size, e);
                return;
            }
            jobs.put(id, curr.withStatus("FAILED", msg, e));
            log.error("Collect job FAILED id={} message={} size={}", id, msg, size, e);
        }
    }

    private void waitUntil(Instant target) {
        long ms = target.toEpochMilli() - System.currentTimeMillis();
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record JobState(String id, String status, String message, Path output, Instant createdAt, Throwable error) {
        JobState withStatus(String status, String message, Throwable error) {
            return new JobState(id, status, message, output, createdAt, error);
        }
    }
}
