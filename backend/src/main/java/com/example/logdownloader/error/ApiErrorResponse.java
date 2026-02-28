package com.example.logdownloader.error;

import java.time.Instant;

public record ApiErrorResponse(Instant timestamp, int status, String error, String path) {
}
