package io.llmplatform.controller;

import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 应用设置。 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public AppSettings get() {
        return settingsService.current();
    }

    @PutMapping
    public AppSettings put(@Valid @RequestBody AppSettings settings) {
        return settingsService.save(settings);
    }
}
