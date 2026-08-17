package io.llmplatform.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 更新单个模型的运行、采样、压缩与高级参数。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelConfigUpdateRequest(
        @Min(256) @Max(131072) int contextSize,
        @Min(1) @Max(256) int threads,
        @Min(0) @Max(999) int gpuLayers,
        @Min(1) @Max(8192) int batchSize,
        @Min(1) @Max(8192) int ubatchSize,
        @NotNull Boolean flashAttention,
        @NotNull Boolean memoryMap,
        @NotNull Boolean memoryLock,
        @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
        @DecimalMin("0.0") @DecimalMax("1.0") double topP,
        @Min(0) @Max(200) int topK,
        @DecimalMin("0.0") @DecimalMax("1.0") double minP,
        @Min(1) @Max(32768) int maxTokens,
        @DecimalMin("0.0") @DecimalMax("2.0") double repeatPenalty,
        @Min(0) @Max(2048) int repeatLastN,
        Integer seed,
        @DecimalMin("0.0") @DecimalMax("2.0") double frequencyPenalty,
        @DecimalMin("-2.0") @DecimalMax("2.0") double presencePenalty,
        List<@Size(max = 64) String> stop,
        @NotNull Boolean compressionEnabled,
        @DecimalMin("0.1") @DecimalMax("1.0") double compressionTriggerRatio,
        @Min(1) @Max(200) int keepRecentMessages,
        @Min(32) @Max(4096) int summaryMaxTokens,
        JsonNode advancedInferenceParams) {}
