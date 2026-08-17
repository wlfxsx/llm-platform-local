package io.llmplatform.pojo.dto;

import jakarta.validation.constraints.NotBlank;

/** 本机路径导入请求。 */
public record PathImportRequest(@NotBlank String path) {}
