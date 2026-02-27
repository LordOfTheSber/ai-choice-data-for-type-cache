package com.example.logdownloader.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LabelSelectorParser {

    private LabelSelectorParser() {
    }

    public static Map<String, String> parseEqualsSelector(String selector) {
        if (selector == null || selector.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, String> labels = new LinkedHashMap<>();
        for (String chunk : selector.split(",")) {
            String part = chunk.trim();
            if (part.isEmpty()) {
                continue;
            }
            int idx = part.indexOf('=');
            if (idx <= 0 || idx == part.length() - 1) {
                continue;
            }
            String key = part.substring(0, idx).trim();
            String value = part.substring(idx + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                labels.put(key, value);
            }
        }
        return labels;
    }
}
