package com.example.logdownloader.util;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public final class LogTimeFilter {

    private LogTimeFilter() {
    }

    public static Optional<Instant> parseTimestamp(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String token = line.split("\\s+", 2)[0].replace("[", "").replace("]", "");
        try {
            return Optional.of(Instant.parse(token));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    public static boolean inRange(Optional<Instant> timestamp, Instant from, Instant to) {
        if (timestamp.isEmpty()) {
            return true;
        }
        Instant ts = timestamp.get();
        boolean afterFrom = from == null || !ts.isBefore(from);
        boolean beforeTo = to == null || !ts.isAfter(to);
        return afterFrom && beforeTo;
    }
}
