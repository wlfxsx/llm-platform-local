package io.llmplatform.pojo.dto;

import jakarta.validation.constraints.NotBlank;

/** 新增 MCP 服务。 */
public record McpCreateRequest(
        @NotBlank String name,
        String transport,
        @NotBlank String commandOrUrl,
        String configJson) {}
