package com.example.logdownloader.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogTimeFilter {

    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter MOSCOW_LOG_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");
    private static final DateTimeFormatter MOSCOW_PICKER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final Pattern MOSCOW_LINE_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})");

    private LogTimeFilter() {
    }

    public static Optional<Instant> parseTimestamp(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = MOSCOW_LINE_PATTERN.matcher(line);
        if (matcher.find()) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(matcher.group(1), MOSCOW_LOG_FORMAT);
                return Optional.of(ldt.atZone(MOSCOW_ZONE).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }

        String token = line.split("\\s+", 2)[0].replace("[", "").replace("]", "");
        try {
            return Optional.of(Instant.parse(token));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    public static Instant parseUserDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(value, MOSCOW_PICKER_FORMAT);
            return ldt.atZone(MOSCOW_ZONE).toInstant();
        } catch (Exception ignored) {
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(value, MOSCOW_LOG_FORMAT);
            return ldt.atZone(MOSCOW_ZONE).toInstant();
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("Unsupported datetime format: " + value);
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
