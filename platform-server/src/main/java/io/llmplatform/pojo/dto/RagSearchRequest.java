package io.llmplatform.pojo.dto;

import jakarta.validation.constraints.NotBlank;

/** 知识库检索请求。 */
public record RagSearchRequest(@NotBlank String query, String sessionId) {}
