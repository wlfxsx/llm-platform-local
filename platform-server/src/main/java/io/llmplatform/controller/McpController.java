package io.llmplatform.controller;

import io.llmplatform.pojo.dto.McpCreateRequest;
import io.llmplatform.pojo.entity.McpServerRecord;
import io.llmplatform.service.McpService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MCP Server 配置与启停。 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @GetMapping
    public List<McpServerRecord> list() {
        return mcpService.list();
    }

    @PostMapping
    public McpServerRecord add(@Valid @RequestBody McpCreateRequest body) {
        return mcpService.add(
                body.name(),
                body.transport() == null || body.transport().isBlank() ? "stdio" : body.transport(),
                body.commandOrUrl(),
                body.configJson());
    }

    @PostMapping("/{id}/enable")
    public void enable(@PathVariable String id) {
        mcpService.setEnabled(id, true);
    }

    @PostMapping("/{id}/disable")
    public void disable(@PathVariable String id) {
        mcpService.setEnabled(id, false);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        mcpService.delete(id);
    }
}
