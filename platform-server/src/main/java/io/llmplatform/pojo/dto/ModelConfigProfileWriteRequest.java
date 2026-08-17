package io.llmplatform.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建或覆盖一套完整的模型参数策略。 */
public record ModelConfigProfileWriteRequest(
        @NotBlank @Size(max = 64) String name, @Valid @NotNull ModelConfigUpdateRequest config) {}
