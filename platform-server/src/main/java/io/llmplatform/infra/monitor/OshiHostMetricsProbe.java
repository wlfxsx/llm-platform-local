package io.llmplatform.infra.monitor;

import io.llmplatform.pojo.vo.GpuResourceMetrics;
import io.llmplatform.pojo.vo.ProcessResourceMetrics;
import io.llmplatform.pojo.vo.SystemResourceMetrics;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** 用 OSHI 采集整机与进程占用，NVIDIA 显卡走短超时 nvidia-smi。 */
@Component
public class OshiHostMetricsProbe implements HostMetricsProbe {

    private static final Logger log = LoggerFactory.getLogger(OshiHostMetricsProbe.class);
    private final SystemInfo systemInfo = new SystemInfo();
    private final Object cpuLock = new Object();
    private final Map<Integer, OSProcess> previousProcesses = new ConcurrentHashMap<>();
    private long[] previousCpuTicks;

    private static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.clamp(value, 0.0, 100.0);
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long parseMibToBytes(String raw) {
        Double mib = parseDouble(raw);
        return mib == null ? null : Math.round(mib * 1024d * 1024d);
    }

    @Override
    public SystemResourceMetrics system() {
        try {
            CentralProcessor processor = systemInfo.getHardware().getProcessor();
            double cpu;
            synchronized (cpuLock) {
                if (previousCpuTicks == null) {
                    previousCpuTicks = processor.getSystemCpuLoadTicks();
                    cpu = 0;
                } else {
                    cpu = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100d;
                    previousCpuTicks = processor.getSystemCpuLoadTicks();
                }
            }
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            long total = memory.getTotal();
            long used = Math.max(0, total - memory.getAvailable());
            return new SystemResourceMetrics(
                    clampPercent(cpu), used, total, processor.getLogicalProcessorCount());
        } catch (Exception ex) {
            log.debug("整机采样失败", ex);
            return new SystemResourceMetrics(0, 0, 0, Runtime.getRuntime().availableProcessors());
        }
    }

    @Override
    public ProcessResourceMetrics process(long pid) {
        try {
            OperatingSystem os = systemInfo.getOperatingSystem();
            OSProcess current = os.getProcess((int) pid);
            if (current == null) {
                previousProcesses.remove((int) pid);
                return ProcessResourceMetrics.unavailable();
            }
            OSProcess previous = previousProcesses.put((int) pid, current);
            double cpu = 0;
            if (previous != null) {
                try {
                    cpu = current.getProcessCpuLoadBetweenTicks(previous) * 100d;
                } catch (Exception ex) {
                    cpu = 0;
                }
            }
            return new ProcessResourceMetrics(
                    true,
                    pid,
                    clampPercent(cpu),
                    current.getResidentSetSize(),
                    current.getThreadCount(),
                    current.getUpTime());
        } catch (Exception ex) {
            log.debug("进程 {} 采样失败", pid, ex);
            return ProcessResourceMetrics.unavailable();
        }
    }

    @Override
    public GpuResourceMetrics gpu() {
        try {
            ProcessBuilder builder =
                    new ProcessBuilder(
                            "nvidia-smi",
                            "--query-gpu=name,utilization.gpu,memory.used,memory.total,temperature.gpu",
                            "--format=csv,noheader,nounits");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try {
                boolean finished = process.waitFor(2, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return GpuResourceMetrics.unavailable();
                }
                if (process.exitValue() != 0) {
                    return GpuResourceMetrics.unavailable();
                }
                try (BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line == null || line.isBlank()) {
                        return GpuResourceMetrics.unavailable();
                    }
                    String[] parts = line.split(",");
                    if (parts.length < 5) {
                        return GpuResourceMetrics.unavailable();
                    }
                    return new GpuResourceMetrics(
                            true,
                            parts[0].trim(),
                            parseDouble(parts[1]),
                            parseMibToBytes(parts[2]),
                            parseMibToBytes(parts[3]),
                            parseDouble(parts[4]));
                }
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (Exception ex) {
            return GpuResourceMetrics.unavailable();
        }
    }
}
