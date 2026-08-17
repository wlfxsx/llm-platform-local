package io.llmplatform.service;

import io.llmplatform.infra.llamafile.LlamafileManager;
import io.llmplatform.infra.monitor.HostMetricsProbe;
import io.llmplatform.infra.monitor.InferenceThroughputTracker;
import io.llmplatform.infra.monitor.LlamafileInferenceSampler;
import io.llmplatform.pojo.vo.GpuResourceMetrics;
import io.llmplatform.pojo.vo.InferenceMetrics;
import io.llmplatform.pojo.vo.LlamafileStatus;
import io.llmplatform.pojo.vo.ProcessResourceMetrics;
import io.llmplatform.pojo.vo.RuntimeMonitorSnapshot;
import io.llmplatform.pojo.vo.SystemResourceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 聚合整机、控制面、模型进程与推理指标；单项失败时降级为空值。 */
@Service
public class RuntimeMonitorService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeMonitorService.class);
    private final LlamafileManager llamafileManager;
    private final HostMetricsProbe hostMetrics;
    private final LlamafileInferenceSampler inferenceSampler;
    private final InferenceThroughputTracker throughputTracker;

    public RuntimeMonitorService(
            LlamafileManager llamafileManager,
            HostMetricsProbe hostMetrics,
            LlamafileInferenceSampler inferenceSampler,
            InferenceThroughputTracker throughputTracker) {
        this.llamafileManager = llamafileManager;
        this.hostMetrics = hostMetrics;
        this.inferenceSampler = inferenceSampler;
        this.throughputTracker = throughputTracker;
    }

    public RuntimeMonitorSnapshot snapshot() {
        LlamafileStatus status = readStatus();
        return new RuntimeMonitorSnapshot(
                System.currentTimeMillis(),
                status,
                readSystem(),
                readProcess(ProcessHandle.current().pid()),
                readModelProcess(),
                readGpu(),
                readInference(status));
    }

    private LlamafileStatus readStatus() {
        try {
            return llamafileManager.status();
        } catch (Exception ex) {
            log.debug("读取 llamafile 状态失败", ex);
            return new LlamafileStatus("stopped", "status.stopped", false, "");
        }
    }

    private SystemResourceMetrics readSystem() {
        try {
            return hostMetrics.system();
        } catch (Exception ex) {
            log.debug("整机采样失败", ex);
            return new SystemResourceMetrics(0, 0, 0, Runtime.getRuntime().availableProcessors());
        }
    }

    private ProcessResourceMetrics readProcess(long pid) {
        try {
            return hostMetrics.process(pid);
        } catch (Exception ex) {
            log.debug("进程 {} 采样失败", pid, ex);
            return ProcessResourceMetrics.unavailable();
        }
    }

    private ProcessResourceMetrics readModelProcess() {
        try {
            Long pid = llamafileManager.pid();
            return pid == null ? ProcessResourceMetrics.unavailable() : readProcess(pid);
        } catch (Exception ex) {
            log.debug("模型进程采样失败", ex);
            return ProcessResourceMetrics.unavailable();
        }
    }

    private GpuResourceMetrics readGpu() {
        try {
            return hostMetrics.gpu();
        } catch (Exception ex) {
            log.debug("GPU 采样失败", ex);
            return GpuResourceMetrics.unavailable();
        }
    }

    private InferenceMetrics readInference(LlamafileStatus status) {
        if (status == null || !status.healthy()) {
            return new InferenceMetrics(null, null, null, null, null);
        }
        InferenceMetrics sampled;
        try {
            sampled = inferenceSampler.sample();
        } catch (Exception ex) {
            log.debug("推理指标采样失败", ex);
            sampled = new InferenceMetrics(null, null, null, null, null);
        }
        return merge(sampled, throughputTracker);
    }

    /** llamafile 未暴露对应指标时回退到平台自测吞吐，避免监控页长期空白。 */
    static InferenceMetrics merge(InferenceMetrics sampled, InferenceThroughputTracker tracker) {
        Double tokensPerSecond =
                sampled.tokensPerSecond() != null && sampled.tokensPerSecond() > 0
                        ? sampled.tokensPerSecond()
                        : tracker.tokensPerSecond();
        Integer active =
                sampled.activeRequests() != null && sampled.activeRequests() > 0
                        ? sampled.activeRequests()
                        : tracker.activeGenerations();
        return new InferenceMetrics(
                active,
                tokensPerSecond,
                sampled.contextUsed(),
                sampled.contextSize(),
                sampled.kvCachePercent());
    }
}
