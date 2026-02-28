package com.example.logdownloader.util;

public final class FileNameSanitizer {
    private FileNameSanitizer() {}

    public static String sanitize(String input) {
        return input == null ? "unknown" : input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
