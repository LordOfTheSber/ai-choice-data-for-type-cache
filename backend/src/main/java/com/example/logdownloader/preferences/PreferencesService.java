package com.example.logdownloader.preferences;

import com.example.logdownloader.dto.UiPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PreferencesService {

    private static final Path STORAGE = Path.of("data/ui-preferences.json");
    private final ObjectMapper objectMapper;

    public PreferencesService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UiPreferences load() {
        try {
            if (!Files.exists(STORAGE)) {
                return new UiPreferences(null, "default", "", null, null, false, null, false, null, null);
            }
            return objectMapper.readValue(Files.readString(STORAGE), UiPreferences.class);
        } catch (Exception e) {
            return new UiPreferences(null, "default", "", null, null, false, null, false, null, null);
        }
    }

    public UiPreferences save(UiPreferences preferences) {
        try {
            Files.createDirectories(STORAGE.getParent());
            Files.writeString(STORAGE, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(preferences));
            return preferences;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save preferences: " + e.getMessage(), e);
        }
    }
}
