package io.llmplatform.pojo.dto;

import jakarta.validation.constraints.NotBlank;

/** 单条对话消息。 */
public record ChatMessage(@NotBlank String role, @NotBlank String content) {}
