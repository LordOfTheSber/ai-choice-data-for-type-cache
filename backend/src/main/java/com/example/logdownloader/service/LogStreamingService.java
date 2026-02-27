package com.example.logdownloader.service;

import com.example.logdownloader.config.K8sClientFactory;
import com.example.logdownloader.dto.DownloadRequest;
import com.example.logdownloader.dto.PreviewRequest;
import com.example.logdownloader.dto.PreviewResponse;
import com.example.logdownloader.error.ApiException;
import com.example.logdownloader.util.FileNameSanitizer;
import com.example.logdownloader.util.LabelSelectorParser;
import com.example.logdownloader.util.LogTimeFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class LogStreamingService {

    private final K8sClientFactory factory;
    private final ObjectMapper objectMapper;

    public LogStreamingService(K8sClientFactory factory, ObjectMapper objectMapper) {
        this.factory = factory;
        this.objectMapper = objectMapper;
    }

    public PreviewResponse preview(PreviewRequest req) {
        int limit = req.lines() == null ? 200 : req.lines();
        List<PreviewResponse.PreviewLine> out = new ArrayList<>();
        AtomicLong unparsed = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        AtomicLong total = new AtomicLong();

        try (KubernetesClient client = createClient(req.contour(), req.masterAccess())) {
            for (String pod : resolvePods(client, req.namespace(), req.pods(), req.selector())) {
                for (String container : resolveContainers(client, req.namespace(), pod, req.containers())) {
                    try {
                        List<String> lines = collectWindowed(client, req.namespace(), pod, container, req.from(), req.to(), req.previous(), req.maxBytes(), req.pollIntervalSeconds(), unparsed);
                        for (String line : lines) {
                            total.incrementAndGet();
                            if (out.size() < limit) {
                                out.add(new PreviewResponse.PreviewLine(pod, container, line));
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        if (!req.bestEffort()) {
                            throw e;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new ApiException(500, "Preview failed: " + e.getMessage());
        }

        return new PreviewResponse(out, new PreviewResponse.PreviewStats(total.get(), unparsed.get(), errors.get()));
    }

    public StreamingResponseBody download(DownloadRequest req) {
        return outputStream -> {
            AtomicLong unparsed = new AtomicLong();
            List<Map<String, Object>> stats = new ArrayList<>();

            try (KubernetesClient client = createClient(req.contour(), req.masterAccess());
                 ZipOutputStream zip = new ZipOutputStream(outputStream)) {

                for (String pod : resolvePods(client, req.namespace(), req.pods(), req.selector())) {
                    for (String container : resolveContainers(client, req.namespace(), pod, req.containers())) {
                        String path = "logs/" + FileNameSanitizer.sanitize(pod) + "/" + FileNameSanitizer.sanitize(container) + ".log";
                        long written = 0L;
                        zip.putNextEntry(new ZipEntry(path));
                        try {
                            List<String> lines = collectWindowed(client, req.namespace(), pod, container, req.from(), req.to(), req.previous(), req.maxBytes(), req.pollIntervalSeconds(), unparsed);
                            for (String line : lines) {
                                byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                                if (maxBytesExceeded(req.maxBytes(), written, bytes.length)) {
                                    break;
                                }
                                zip.write(bytes);
                                written += bytes.length;
                            }
                            stats.add(Map.of("pod", pod, "container", container, "bytes", written, "status", "ok"));
                        } catch (Exception e) {
                            zip.closeEntry();
                            writeError(zip, pod, e.getMessage());
                            stats.add(Map.of("pod", pod, "container", container, "status", "error", "error", e.getMessage()));
                            if (!req.bestEffort()) {
                                throw e;
                            }
                            continue;
                        }
                        zip.closeEntry();
                    }
                }

                zip.putNextEntry(new ZipEntry("metadata.json"));
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("contour", req.contour());
                metadata.put("namespace", req.namespace());
                metadata.put("selector", req.selector());
                metadata.put("workloadKind", req.workloadKind());
                metadata.put("workloadName", req.workloadName());
                metadata.put("from", req.from());
                metadata.put("to", req.to());
                metadata.put("pollIntervalSeconds", req.pollIntervalSeconds());
                metadata.put("pods", req.pods());
                metadata.put("containers", req.containers());
                metadata.put("stats", stats);
                metadata.put("unparsedTimestampCount", unparsed.get());
                objectMapper.writeValue(zip, metadata);
                zip.closeEntry();
                zip.finish();
            }
        };
    }

    private List<String> collectWindowed(KubernetesClient client,
                                         String namespace,
                                         String pod,
                                         String container,
                                         Instant from,
                                         Instant to,
                                         boolean previous,
                                         Long maxBytes,
                                         Integer pollIntervalSeconds,
                                         AtomicLong unparsedCounter) throws IOException {
        List<String> result = new ArrayList<>();
        long emittedBytes = 0L;

        if (from == null || to == null || !from.isBefore(to)) {
            try (BufferedReader br = logReader(client, namespace, pod, container, from, previous)) {
                String line;
                while ((line = br.readLine()) != null) {
                    var ts = LogTimeFilter.parseTimestamp(line);
                    if (ts.isEmpty()) {
                        unparsedCounter.incrementAndGet();
                    }
                    if (!LogTimeFilter.inRange(ts, from, to)) {
                        continue;
                    }
                    byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                    if (maxBytesExceeded(maxBytes, emittedBytes, bytes.length)) {
                        break;
                    }
                    emittedBytes += bytes.length;
                    result.add(line);
                }
            }
            return result;
        }

        int sec = pollIntervalSeconds == null || pollIntervalSeconds <= 0 ? 30 : pollIntervalSeconds;
        Duration chunk = Duration.ofSeconds(sec);
        Instant cursor = from;

        while (cursor.isBefore(to)) {
            Instant windowEnd = cursor.plus(chunk);
            if (windowEnd.isAfter(to)) {
                windowEnd = to;
            }

            try (BufferedReader br = logReader(client, namespace, pod, container, cursor, previous)) {
                String line;
                while ((line = br.readLine()) != null) {
                    var ts = LogTimeFilter.parseTimestamp(line);
                    if (ts.isEmpty()) {
                        unparsedCounter.incrementAndGet();
                        continue;
                    }
                    Instant parsed = ts.get();
                    if (parsed.isBefore(cursor) || parsed.isAfter(windowEnd)) {
                        continue;
                    }
                    byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                    if (maxBytesExceeded(maxBytes, emittedBytes, bytes.length)) {
                        return result;
                    }
                    emittedBytes += bytes.length;
                    result.add(line);
                }
            }
            cursor = windowEnd.plusMillis(1);
        }

        return result;
    }

    private KubernetesClient createClient(String contour, boolean master) {
        return master ? factory.createClientForMaster(contour) : factory.createClient(contour);
    }

    private List<String> resolvePods(KubernetesClient client, String namespace, List<String> pods, String selector) {
        if (pods != null && !pods.isEmpty()) {
            return pods;
        }
        if (StringUtils.hasText(selector)) {
            return client.pods()
                    .inNamespace(namespace)
                    .withLabels(LabelSelectorParser.parseEqualsSelector(selector))
                    .list()
                    .getItems()
                    .stream()
                    .map(p -> p.getMetadata().getName())
                    .sorted()
                    .toList();
        }
        return client.pods().inNamespace(namespace).list().getItems().stream().map(p -> p.getMetadata().getName()).sorted().toList();
    }

    private List<String> resolveContainers(KubernetesClient client, String namespace, String pod, List<String> containers) {
        if (containers != null && !containers.isEmpty()) {
            return containers;
        }
        var resource = client.pods().inNamespace(namespace).withName(pod).get();
        if (resource == null || resource.getSpec() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        if (resource.getSpec().getContainers() != null) {
            names.addAll(resource.getSpec().getContainers().stream().map(c -> c.getName()).toList());
        }
        if (resource.getSpec().getInitContainers() != null) {
            names.addAll(resource.getSpec().getInitContainers().stream().map(c -> c.getName()).toList());
        }
        if (resource.getSpec().getEphemeralContainers() != null) {
            names.addAll(resource.getSpec().getEphemeralContainers().stream().map(c -> c.getName()).toList());
        }
        return names.stream().distinct().sorted().toList();
    }

    private BufferedReader logReader(KubernetesClient client, String namespace, String pod, String container,
                                     Instant from, boolean previous) {
        var base = client.pods().inNamespace(namespace).withName(pod).inContainer(container);
        Reader reader;

        if (previous) {
            if (from != null) {
                reader = base.terminated().sinceTime(from.toString()).getLogReader();
            } else {
                reader = base.terminated().getLogReader();
            }
        } else {
            if (from != null) {
                reader = base.sinceTime(from.toString()).getLogReader();
            } else {
                reader = base.getLogReader();
            }
        }

        return new BufferedReader(reader);
    }

    private boolean maxBytesExceeded(Long maxBytes, long alreadyWritten, int nextLineBytes) {
        return maxBytes != null && maxBytes > 0 && alreadyWritten + nextLineBytes > maxBytes;
    }

    private void writeError(ZipOutputStream zip, String pod, String error) throws IOException {
        zip.putNextEntry(new ZipEntry("errors/" + FileNameSanitizer.sanitize(pod) + ".txt"));
        zip.write((error + "\n").getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
