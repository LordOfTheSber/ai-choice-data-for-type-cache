package com.example.logdownloader.controller;

import com.example.logdownloader.dto.*;
import com.example.logdownloader.service.K8sQueryService;
import com.example.logdownloader.service.LogCollectJobService;
import com.example.logdownloader.service.LogStreamingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class K8sController {

    private final K8sQueryService queryService;
    private final LogStreamingService logs;
    private final LogCollectJobService jobs;

    public K8sController(K8sQueryService queryService, LogStreamingService logs, LogCollectJobService jobs) {
        this.queryService = queryService;
        this.logs = logs;
        this.jobs = jobs;
    }

    @GetMapping("/contours")
    public List<String> contours() { return queryService.contours(); }

    @GetMapping("/namespaces")
    public List<String> namespaces(@RequestParam(required = false) String contour) { return queryService.namespaces(contour); }

    @GetMapping("/workloads")
    public List<String> workloads(@RequestParam(required = false) String contour,
                                  @RequestParam String namespace,
                                  @RequestParam WorkloadKind kind) { return queryService.workloads(contour, namespace, kind); }

    @GetMapping("/pods")
    public List<String> pods(@RequestParam(required = false) String contour,
                             @RequestParam String namespace,
                             @RequestParam(required = false) String selector) { return queryService.pods(contour, namespace, selector); }

    @GetMapping("/pods/{pod}/containers")
    public List<String> containers(@PathVariable String pod,
                                   @RequestParam(required = false) String contour,
                                   @RequestParam String namespace) { return queryService.containers(contour, namespace, pod); }

    @GetMapping("/containers")
    public List<String> containersForSelection(@RequestParam(required = false) String contour,
                                               @RequestParam String namespace,
                                               @RequestParam(required = false) List<String> pods,
                                               @RequestParam(required = false) String selector) {
        return queryService.containersForPods(contour, namespace, pods, selector);
    }

    @PostMapping("/logs/preview")
    public PreviewResponse preview(@Valid @RequestBody PreviewRequest request) { return logs.preview(request); }

    @PostMapping(value = "/logs/download", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> download(@Valid @RequestBody DownloadRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=logs.zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(logs.download(request));
    }

    @PostMapping("/logs/collect")
    public LogCollectStartResponse startCollect(@Valid @RequestBody DownloadRequest request) {
        return jobs.start(request);
    }

    @GetMapping("/logs/collect/{jobId}")
    public LogCollectStatusResponse status(@PathVariable String jobId) {
        return jobs.status(jobId);
    }

    @GetMapping(value = "/logs/collect/{jobId}/download", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> downloadCollected(@PathVariable String jobId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=collected-logs.zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(jobs.download(jobId));
    }
}
