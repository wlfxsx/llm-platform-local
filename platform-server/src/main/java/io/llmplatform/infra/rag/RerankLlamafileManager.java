package io.llmplatform.infra.rag;

import io.llmplatform.common.UserDataPaths;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.config.PlatformProperties;
import io.llmplatform.infra.llamafile.LlamafileBinaryLocator;
import io.llmplatform.infra.llamafile.ManagedLlamafileServer;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.pojo.vo.LlamafileStatus;
import io.llmplatform.service.SettingsService;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 独立 rerank llamafile：bge-reranker-v2-m3、默认 CPU。 */
@Component
public class RerankLlamafileManager {

    private static final Logger log = LoggerFactory.getLogger(RerankLlamafileManager.class);
    private final PlatformProperties properties;
    private final SettingsService settingsService;
    private final LlamafileBinaryLocator binaryLocator;
    private final BuiltinRagModels models;
    private final UserDataPaths paths;
    private final ManagedLlamafileServer server = new ManagedLlamafileServer("rerank");

    public RerankLlamafileManager(
            PlatformProperties properties,
            SettingsService settingsService,
            LlamafileBinaryLocator binaryLocator,
            BuiltinRagModels models,
            UserDataPaths paths) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.binaryLocator = binaryLocator;
        this.models = models;
        this.paths = paths;
    }

    public synchronized boolean ensureReady() {
        paths.ensureDirectories();
        Path model = models.rerankModel();
        if (model == null) {
            return false;
        }
        AppSettings settings = ensureFreePort(settingsService.current());
        if (server.healthy(endpoint(settings))) {
            return true;
        }
        try {
            List<String> command = baseCommand(settings, model);
            command.add("--reranking");
            server.start(command, endpoint(settings));
            return true;
        } catch (PlatformException ex) {
            log.info("rerank 进程未能启动，检索将仅使用 RRF");
            return false;
        }
    }

    public LlamafileStatus status() {
        AppSettings settings = settingsService.current();
        Path model = models.rerankModel();
        return server.status(
                endpoint(settings),
                model == null ? BuiltinRagModels.RERANK_FILE : model.getFileName().toString());
    }

    public String endpoint() {
        return endpoint(settingsService.current());
    }

    public boolean modelPresent() {
        return models.rerankPresent();
    }

    @PreDestroy
    public void shutdown() {
        server.stop();
    }

    private List<String> baseCommand(AppSettings settings, Path model) {
        List<String> command = new ArrayList<>();
        command.add(binaryLocator.resolve(settings).toString());
        command.add("-m");
        command.add(model.toString());
        command.add("--server");
        command.add("--host");
        command.add(properties.getLlamafile().getHost());
        command.add("--port");
        command.add(String.valueOf(settings.rerankLlamafilePort()));
        command.add("-c");
        command.add("2048");
        command.add("-ngl");
        command.add("0");
        command.add("--gpu");
        command.add("disable");
        return command;
    }

    private AppSettings ensureFreePort(AppSettings settings) {
        int configured = settings.rerankLlamafilePort();
        if (isPortFree(configured) || server.healthy(endpoint(settings))) {
            return settings;
        }
        for (int port = configured + 1; port < configured + 32 && port <= 65535; port++) {
            if (isPortFree(port)) {
                log.warn("rerank 端口 {} 已被占用，改用 {}", configured, port);
                return settingsService.save(settings.withRerankLlamafilePort(port));
            }
        }
        throw new PlatformException("LLAMAFILE_FAILED", "error.llamafilePortBusy");
    }

    private boolean isPortFree(int port) {
        try (ServerSocket socket =
                new ServerSocket(
                        port, 1, InetAddress.getByName(properties.getLlamafile().getHost()))) {
            return socket.getLocalPort() == port;
        } catch (Exception ex) {
            return false;
        }
    }

    private String endpoint(AppSettings settings) {
        return "http://"
                + properties.getLlamafile().getHost()
                + ":"
                + settings.rerankLlamafilePort();
    }
}
