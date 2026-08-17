package io.llmplatform.pojo.vo;

/** NVIDIA 采样结果；其它厂商或命令不可用时 available=false。 */
public record GpuResourceMetrics(
        boolean available,
        String name,
        Double utilizationPercent,
        Long memoryUsedBytes,
        Long memoryTotalBytes,
        Double temperatureC) {

    public static GpuResourceMetrics unavailable() {
        return new GpuResourceMetrics(false, null, null, null, null, null);
    }
}
