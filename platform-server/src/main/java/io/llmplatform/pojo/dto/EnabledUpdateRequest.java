package io.llmplatform.pojo.dto;

import jakarta.validation.constraints.NotNull;

/** 开关类更新请求。 */
public record EnabledUpdateRequest(@NotNull Boolean enabled) {}
