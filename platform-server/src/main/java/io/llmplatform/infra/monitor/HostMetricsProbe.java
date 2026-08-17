package io.llmplatform.infra.monitor;

import io.llmplatform.pojo.vo.GpuResourceMetrics;
import io.llmplatform.pojo.vo.ProcessResourceMetrics;
import io.llmplatform.pojo.vo.SystemResourceMetrics;

/** 可替换的本机采样，便于测试注入假数据。 */
public interface HostMetricsProbe {

    SystemResourceMetrics system();

    ProcessResourceMetrics process(long pid);

    GpuResourceMetrics gpu();
}
