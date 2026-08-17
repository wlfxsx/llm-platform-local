package io.llmplatform.infra.llamafile;

import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.vo.LlamafileStatus;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 管理单个辅助 llamafile 进程，停止时只杀自己的 pid，避免误伤对话进程。 */
public final class ManagedLlamafileServer {

    private static final Logger log = LoggerFactory.getLogger(ManagedLlamafileServer.class);
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicReference<Process> process = new AtomicReference<>();
    private final AtomicLong startedAt = new AtomicLong(0);
    private final AtomicReference<Thread> outputDrainer = new AtomicReference<>();
    private final String label;

    public ManagedLlamafileServer(String label) {
        this.label = label;
    }

    public synchronized void start(List<String> command, String endpoint) {
        if (healthy(endpoint)) {
            return;
        }
        stop();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process started = builder.start();
            process.set(started);
            startedAt.set(System.currentTimeMillis());
            drainOutput(started);
            log.info("已启动 {} llamafile pid={}", label, started.pid());
            waitUntilHealthy(endpoint, started);
        } catch (IOException ex) {
            stop();
            throw new PlatformException("LLAMAFILE_FAILED", "error.embeddingLlamafileFailed");
        }
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
            log.info("已停止 {} llamafile pid={}", label, pid);
        }
    }

    public LlamafileStatus status(String endpoint, String modelName) {
        Process running = process.get();
        boolean alive = running != null && running.isAlive();
        boolean healthy = healthy(endpoint);
        String state = healthy ? "ready" : alive ? "starting" : "stopped";
        String key = healthy ? "status.ready" : alive ? "status.starting" : "status.stopped";
        Long pid = alive ? running.pid() : null;
        Long started = alive ? startedAt.get() : null;
        return new LlamafileStatus(state, key, healthy, endpoint, pid, started, label, modelName);
    }

    public boolean healthy(String endpoint) {
        String health = fetch(endpoint + "/health");
        if (health != null && health.contains("\"status\"")) {
            return true;
        }
        String models = fetch(endpoint + "/v1/models");
        return models != null && (models.contains("\"data\"") || models.contains("\"object\""));
    }

    private void waitUntilHealthy(String endpoint, Process started) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (!started.isAlive()) {
                throw new PlatformException("LLAMAFILE_FAILED", "error.embeddingLlamafileFailed");
            }
            if (healthy(endpoint)) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new PlatformException("LLAMAFILE_FAILED", "error.embeddingLlamafileFailed");
            }
        }
        throw new PlatformException("LLAMAFILE_FAILED", "error.embeddingLlamafileFailed");
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
                        label + "-llamafile-stdout");
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
}
