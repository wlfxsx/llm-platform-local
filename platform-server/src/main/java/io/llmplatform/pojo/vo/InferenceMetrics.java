package io.llmplatform.pojo.vo;

/** llamafile 推理层指标；单项缺失时为 null，不阻断整页。 */
public record InferenceMetrics(
        Integer activeRequests,
        Double tokensPerSecond,
        Integer contextUsed,
        Integer contextSize,
        Double kvCachePercent) {}
