package com.example.logdownloader.controller;

import com.example.logdownloader.dto.UiPreferences;
import com.example.logdownloader.preferences.PreferencesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferencesController {

    private final PreferencesService preferencesService;

    public PreferencesController(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public UiPreferences get() {
        return preferencesService.load();
    }

    @PutMapping
    public UiPreferences put(@RequestBody UiPreferences preferences) {
        return preferencesService.save(preferences);
    }
}
