package io.llmplatform.pojo.vo;

/** 监控快照：先整机资源，再平台/模型进程与推理指标。 */
public record RuntimeMonitorSnapshot(
        long sampledAt,
        LlamafileStatus llamafile,
        SystemResourceMetrics system,
        ProcessResourceMetrics platformProcess,
        ProcessResourceMetrics modelProcess,
        GpuResourceMetrics gpu,
        InferenceMetrics inference) {}
