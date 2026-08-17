package io.llmplatform.infra.llamafile;

import io.llmplatform.common.error.PlatformException;
import io.llmplatform.config.PlatformProperties;
import io.llmplatform.infra.hardware.GpuVendors;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.pojo.entity.ModelRecord;
import io.llmplatform.pojo.vo.HardwareDevice;
import io.llmplatform.pojo.vo.LlamafileStatus;
import io.llmplatform.repository.ModelConfigRepository;
import io.llmplatform.repository.ModelRepository;
import io.llmplatform.repository.entity.ModelConfigEntity;
import io.llmplatform.service.HardwareService;
import io.llmplatform.service.SettingsService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 管理本机 llamafile 进程，不把生命周期交给 AgentScope。 */
@Component
public class LlamafileManager {

    private static final Logger log = LoggerFactory.getLogger(LlamafileManager.class);
    private final PlatformProperties properties;
    private final SettingsService settingsService;
    private final ModelRepository modelRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final HardwareService hardwareService;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicReference<Process> process = new AtomicReference<>();
    private final AtomicLong startedAt = new AtomicLong(0);
    private final AtomicReference<Thread> outputDrainer = new AtomicReference<>();

    public LlamafileManager(
            PlatformProperties properties,
            SettingsService settingsService,
            ModelRepository modelRepository,
            ModelConfigRepository modelConfigRepository,
            HardwareService hardwareService) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.modelRepository = modelRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.hardwareService = hardwareService;
    }

    /** 仅在显式启动时加载当前模型。已就绪则直接返回，避免把“启动”做成隐式重启。 */
    public synchronized LlamafileStatus start() {
        AppSettings settings = settingsService.current();
        Path model = resolveCurrentModel(settings);
        if (model == null) {
            throw new PlatformException("MODEL_NOT_READY", "error.modelNotReady");
        }
        if (isActive()) {
            return status();
        }
        stop();
        Path binary = resolveBinary(settings);
        settings = ensureFreePort(settings);
        ModelConfigEntity config = currentConfig(settings);
        List<String> command = new ArrayList<>();
        command.add(binary.toString());
        command.add("-m");
        command.add(model.toString());
        command.add("--server");
        command.add("--host");
        command.add(properties.getLlamafile().getHost());
        command.add("--port");
        command.add(String.valueOf(settings.llamafilePort()));
        command.add("--metrics");
        command.add("-c");
        command.add(String.valueOf(config.getContextSize()));
        command.add("-t");
        command.add(String.valueOf(config.getThreads()));
        command.add("-b");
        command.add(String.valueOf(config.getBatchSize()));
        command.add("-ub");
        command.add(String.valueOf(config.getUbatchSize()));
        applyHardware(command, hardwareService.resolve(settings.hardwareDeviceId()), config);
        if (Boolean.TRUE.equals(config.getFlashAttention())) {
            command.add("-fa");
            command.add("on");
        }
        command.add(Boolean.FALSE.equals(config.getMemoryMap()) ? "--no-mmap" : "--mmap");
        if (Boolean.TRUE.equals(config.getMemoryLock())) {
            command.add("--mlock");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process started = builder.start();
            process.set(started);
            startedAt.set(System.currentTimeMillis());
            drainOutput(started);
            log.info("已启动 llamafile pid={} 端口 {}", started.pid(), settings.llamafilePort());
            return status();
        } catch (IOException ex) {
            throw new PlatformException("LLAMAFILE_FAILED", "error.llamafileFailed");
        }
    }

    /**
     * 配置端口被其它应用占用时改用相邻空闲端口并持久化。
     *
     * <p>端口目前不在界面上暴露，若直接报错用户无处可改；自动让位比启动失败更可用。
     */
    private AppSettings ensureFreePort(AppSettings settings) {
        int configured = settings.llamafilePort();
        if (isPortFree(configured)) {
            return settings;
        }
        for (int port = configured + 1; port < configured + 32 && port <= 65535; port++) {
            if (isPortFree(port)) {
                log.warn("端口 {} 已被其它程序占用，llamafile 改用 {}", configured, port);
                return settingsService.save(settings.withLlamafilePort(port));
            }
        }
        throw new PlatformException("LLAMAFILE_FAILED", "error.llamafilePortBusy");
    }

    private boolean isPortFree(int port) {
        try (ServerSocket socket =
                new ServerSocket(
                        port, 1, InetAddress.getByName(properties.getLlamafile().getHost()))) {
            return socket.getLocalPort() == port;
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * 把所选推理硬件翻译成 llamafile 参数。
     *
     * <p>选 CPU 时显式禁用 GPU，避免 llamafile 自动挑到显卡；选显卡时按后端显式指定， 并在预设未设置卸载层数时整模型上卡，否则会出现“选了显卡仍跑
     * CPU”。同型号后端下无法再区分具体设备， 独显与核显都只能走 Vulkan 时以驱动枚举的第一块为准。
     */
    static void applyHardware(
            List<String> command, HardwareDevice device, ModelConfigEntity config) {
        if (device == null || "cpu".equals(device.type())) {
            command.add("--gpu");
            command.add("disable");
            return;
        }
        command.add("--gpu");
        command.add(GpuVendors.backendOf(device.name()));
        command.add("-ngl");
        command.add(String.valueOf(config.getGpuLayers() == null ? 999 : config.getGpuLayers()));
    }

    @PreDestroy
    public void shutdown() {
        stop();
    }

    public synchronized void stop() {
        Process running = process.getAndSet(null);
        startedAt.set(0);
        interruptDrainer();
        if (running != null && running.isAlive()) {
            long pid = running.pid();
            running.destroy();
            try {
                if (!running.waitFor(8, TimeUnit.SECONDS)) {
                    running.destroyForcibly();
                    running.waitFor(3, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running.destroyForcibly();
            }
            log.info("已停止 llamafile pid={}", pid);
        }
        destroyOrphanLlamafile();
    }

    /** 就绪或启动中都视为占用进程，禁止改配置、切换和删除当前模型。 */
    public boolean isActive() {
        String state = status().state();
        return "ready".equals(state) || "starting".equals(state);
    }

    public void requireStopped() {
        if (isActive()) {
            throw new PlatformException("MODEL_RUNNING", "error.modelRunning");
        }
    }

    public Long pid() {
        Process running = process.get();
        return running != null && running.isAlive() ? running.pid() : null;
    }

    public Long startedAtMillis() {
        long value = startedAt.get();
        return value > 0 ? value : null;
    }

    public LlamafileStatus status() {
        AppSettings settings = settingsService.current();
        Process running = process.get();
        boolean alive = running != null && running.isAlive();
        // 桌面或后端重启后可能认不到旧进程，但端口上的服务仍可用；探活成功即视为就绪。
        boolean healthy = ping(settings);
        String state = healthy ? "ready" : alive ? "starting" : "stopped";
        String key = healthy ? "status.ready" : alive ? "status.starting" : "status.stopped";
        ModelRecord current = currentModelRecord(settings);
        Long pid = alive ? running.pid() : null;
        Long started = alive ? startedAtMillis() : null;
        return new LlamafileStatus(
                state,
                key,
                healthy,
                endpoint(settings),
                pid,
                started,
                current == null ? blankToNull(settings.currentModelId()) : current.id(),
                current == null ? null : current.name());
    }

    public String endpoint() {
        return endpoint(settingsService.current());
    }

    private String endpoint(AppSettings settings) {
        return "http://" + properties.getLlamafile().getHost() + ":" + settings.llamafilePort();
    }

    private ModelConfigEntity currentConfig(AppSettings settings) {
        String modelId = settings.currentModelId();
        if (modelId == null || modelId.isBlank()) {
            return ModelConfigEntity.defaults("");
        }
        return modelConfigRepository
                .findByModelId(modelId)
                // 旧库异常缺行时仍允许启动；服务层随后会补建同一默认模板。
                .orElseGet(() -> ModelConfigEntity.defaults(modelId));
    }

    /**
     * 探活必须确认端口上确实是 llamafile。
     *
     * <p>回环端口常被其它本机应用占用（例如 8080 上的 Steam 组件），只看 HTTP 状态码会把它误判成模型已就绪， 于是界面显示可用但对话必然失败；这里改为校验响应内容特征。
     */
    private boolean ping(AppSettings settings) {
        String health = fetch(endpoint(settings) + "/health");
        if (health != null && health.contains("\"status\"")) {
            return true;
        }
        String models = fetch(endpoint(settings) + "/v1/models");
        // llamafile 兼容 OpenAI 协议，模型列表固定包含 data 数组或 object 字段。
        return models != null && (models.contains("\"data\"") || models.contains("\"object\""));
    }

    private String fetch(String url) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 500 ? null : response.body();
        } catch (Exception ex) {
            return null;
        }
    }

    private Path resolveCurrentModel(AppSettings settings) {
        ModelRecord current = currentModelRecord(settings);
        if (current == null) {
            return null;
        }
        Path path = Path.of(current.filePath());
        return Files.isRegularFile(path) ? path : null;
    }

    private ModelRecord currentModelRecord(AppSettings settings) {
        if (settings.currentModelId() == null || settings.currentModelId().isBlank()) {
            return null;
        }
        return modelRepository.findById(settings.currentModelId()).orElse(null);
    }

    private Path resolveBinary(AppSettings settings) {
        List<String> candidates = new ArrayList<>();
        if (settings.llamafileBinary() != null && !settings.llamafileBinary().isBlank()) {
            candidates.add(settings.llamafileBinary());
        }
        if (!properties.getLlamafile().getBinaryPath().isBlank()) {
            candidates.add(properties.getLlamafile().getBinaryPath());
        }
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            candidates.add("llamafile.exe");
        } else {
            candidates.add("llamafile");
        }
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath();
            }
        }
        throw new PlatformException("LLAMAFILE_FAILED", "error.llamafileFailed");
    }

    private void drainOutput(Process started) {
        Thread previous = outputDrainer.getAndSet(null);
        if (previous != null) {
            previous.interrupt();
        }
        Thread drainer =
                new Thread(
                        () -> {
                            try (InputStream input = started.getInputStream()) {
                                byte[] buffer = new byte[4096];
                                while (!Thread.currentThread().isInterrupted()
                                        && input.read(buffer) >= 0) {
                                    // 丢弃标准输出，避免管道塞满后推理进程卡住。
                                }
                            } catch (Exception ignored) {
                                // 进程退出或被中断时结束读取即可。
                            }
                        },
                        "llamafile-stdout");
        drainer.setDaemon(true);
        outputDrainer.set(drainer);
        drainer.start();
    }

    private void interruptDrainer() {
        Thread drainer = outputDrainer.getAndSet(null);
        if (drainer != null) {
            drainer.interrupt();
        }
    }

    private void destroyOrphanLlamafile() {
        AppSettings settings = settingsService.current();
        int port = settings.llamafilePort();
        Path binary = null;
        try {
            binary = resolveBinary(settings).toAbsolutePath().normalize();
        } catch (PlatformException ignored) {
            // 二进制无法解析时仍按进程名和端口清扫，避免退出后残留占用。
        }
        Path expectedBinary = binary;
        ProcessHandle.allProcesses()
                .filter(handle -> isOrphanLlamafile(handle, expectedBinary, port))
                .forEach(this::destroyHandle);
    }

    private boolean isOrphanLlamafile(ProcessHandle handle, Path expectedBinary, int port) {
        Optional<String> command = handle.info().command();
        Optional<String> commandLine = handle.info().commandLine();
        boolean binaryMatch =
                expectedBinary != null
                        && command.map(
                                        value ->
                                                Path.of(value)
                                                        .toAbsolutePath()
                                                        .normalize()
                                                        .equals(expectedBinary))
                                .orElse(false);
        boolean nameMatch =
                command.map(
                                value -> {
                                    String name =
                                            Path.of(value)
                                                    .getFileName()
                                                    .toString()
                                                    .toLowerCase(Locale.ROOT);
                                    return name.equals("llamafile") || name.equals("llamafile.exe");
                                })
                        .orElse(false);
        boolean portMatch =
                commandLine
                        .map(line -> line.contains("--port") && line.contains(String.valueOf(port)))
                        .orElse(false);
        boolean serverMatch = commandLine.map(line -> line.contains("--server")).orElse(false);
        return binaryMatch || (nameMatch && (portMatch || serverMatch));
    }

    private void destroyHandle(ProcessHandle handle) {
        long pid = handle.pid();
        handle.destroy();
        try {
            handle.onExit().orTimeout(3, TimeUnit.SECONDS).join();
        } catch (Exception ignored) {
            handle.destroyForcibly();
        }
        log.info("已清理孤儿 llamafile pid={}", pid);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
