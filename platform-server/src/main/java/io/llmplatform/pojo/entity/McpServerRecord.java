package io.llmplatform.pojo.entity;

/** MCP Server 配置。 */
public record McpServerRecord(
        String id,
        String name,
        String transport,
        String commandOrUrl,
        boolean enabled,
        String configJson) {}
