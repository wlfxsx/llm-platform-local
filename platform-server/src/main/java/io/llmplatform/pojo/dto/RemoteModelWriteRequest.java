package io.llmplatform.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/** 创建或更新远程 OpenAI 兼容配置；apiKey 仅在提交时写入凭据库。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteModelWriteRequest(
        @NotBlank String name,
        @NotBlank String baseUrl,
        @NotBlank String modelName,
        String apiKey) {}
