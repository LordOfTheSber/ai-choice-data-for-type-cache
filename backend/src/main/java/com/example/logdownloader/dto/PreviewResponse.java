package com.example.logdownloader.dto;

import java.util.List;

public record PreviewResponse(List<PreviewLine> lines, PreviewStats stats) {
    public record PreviewLine(String pod, String container, String line) {}
    public record PreviewStats(long totalLines, long unparsedTimestampCount, long errors) {}
}
