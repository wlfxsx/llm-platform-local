package io.llmplatform.pojo.vo;

/** 单个进程的资源占用；模型未运行时 available=false。 */
public record ProcessResourceMetrics(
        boolean available,
        Long pid,
        double cpuPercent,
        long rssBytes,
        int threadCount,
        Long uptimeMs) {

    public static ProcessResourceMetrics unavailable() {
        return new ProcessResourceMetrics(false, null, 0, 0, 0, null);
    }
}
