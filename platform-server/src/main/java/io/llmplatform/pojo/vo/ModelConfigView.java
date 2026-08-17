package io.llmplatform.pojo.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 单个模型的可编辑运行参数。 */
public record ModelConfigView(
        String modelId,
        int contextSize,
        int threads,
        int gpuLayers,
        int batchSize,
        int ubatchSize,
        boolean flashAttention,
        boolean memoryMap,
        boolean memoryLock,
        double temperature,
        double topP,
        int topK,
        double minP,
        int maxTokens,
        double repeatPenalty,
        int repeatLastN,
        Integer seed,
        double frequencyPenalty,
        double presencePenalty,
        List<String> stop,
        boolean compressionEnabled,
        double compressionTriggerRatio,
        int keepRecentMessages,
        int summaryMaxTokens,
        JsonNode advancedInferenceParams,
        boolean processRestarted) {}
