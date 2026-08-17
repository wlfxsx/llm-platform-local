package io.llmplatform.controller;

import io.llmplatform.pojo.dto.PathImportRequest;
import io.llmplatform.pojo.entity.PluginManifest;
import io.llmplatform.pojo.entity.PluginRecord;
import io.llmplatform.service.PluginService;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 插件导入、导出、删除与热插拔。 */
@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private final PluginService pluginService;

    public PluginController(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    @GetMapping
    public List<PluginRecord> list() {
        return pluginService.list();
    }

    @PostMapping("/import")
    public PluginRecord importPlugin(@Valid @RequestBody PathImportRequest body) {
        return pluginService.importPackage(Path.of(body.path()));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<Resource> exportPlugin(@PathVariable String id) {
        Path file = pluginService.exportPackage(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }

    @PostMapping("/{id}/enable")
    public void enable(@PathVariable String id) {
        pluginService.setEnabled(id, true);
    }

    @PostMapping("/{id}/disable")
    public void disable(@PathVariable String id) {
        pluginService.setEnabled(id, false);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        pluginService.delete(id);
    }

    @GetMapping("/{id}/capabilities")
    public PluginManifest capabilities(@PathVariable String id) {
        return pluginService.manifest(id);
    }

    @PostMapping("/{id}/capabilities/validate")
    public Map<String, Object> validate(@PathVariable String id) {
        return pluginService.validate(id);
    }
}
