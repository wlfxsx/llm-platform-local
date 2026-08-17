package io.llmplatform.pojo.vo;

/** 整机 CPU 与物理内存占用。 */
public record SystemResourceMetrics(
        double cpuPercent, long memoryUsedBytes, long memoryTotalBytes, int logicalProcessors) {}
