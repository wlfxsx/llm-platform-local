package io.llmplatform.infra.embedding;

import io.llmplatform.common.UserDataPaths;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.config.PlatformProperties;
import io.llmplatform.infra.llamafile.LlamafileBinaryLocator;
import io.llmplatform.infra.llamafile.ManagedLlamafileServer;
import io.llmplatform.infra.rag.BuiltinRagModels;
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

/** 独立 embedding llamafile：BGE-M3、默认 CPU，不与对话进程抢 GPU。 */
@Component
public class EmbeddingLlamafileManager {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingLlamafileManager.class);
    private final PlatformProperties properties;
    private final SettingsService settingsService;
    private final LlamafileBinaryLocator binaryLocator;
    private final BuiltinRagModels models;
    private final UserDataPaths paths;
    private final ManagedLlamafileServer server = new ManagedLlamafileServer("embedding");

    public EmbeddingLlamafileManager(
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

    public synchronized void ensureReady() {
        paths.ensureDirectories();
        AppSettings settings = settingsService.current();
        Path model = models.embeddingModel();
        if (model == null) {
            throw new PlatformException("EMBEDDING_MODEL_MISSING", "error.embeddingModelMissing");
        }
        settings = ensureFreePort(settings);
        if (server.healthy(endpoint(settings))) {
            return;
        }
        List<String> command = baseCommand(settings, model);
        command.add("--embedding");
        server.start(command, endpoint(settings));
    }

    public LlamafileStatus status() {
        AppSettings settings = settingsService.current();
        Path model = models.embeddingModel();
        return server.status(
                endpoint(settings),
                model == null ? BuiltinRagModels.EMBEDDING_FILE : model.getFileName().toString());
    }

    public String endpoint() {
        return endpoint(settingsService.current());
    }

    public boolean modelPresent() {
        return models.embeddingPresent();
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
        command.add(String.valueOf(settings.embeddingLlamafilePort()));
        command.add("-c");
        command.add("2048");
        command.add("-ngl");
        command.add("0");
        command.add("--gpu");
        command.add("disable");
        return command;
    }

    private AppSettings ensureFreePort(AppSettings settings) {
        int configured = settings.embeddingLlamafilePort();
        if (isPortFree(configured) || server.healthy(endpoint(settings))) {
            return settings;
        }
        for (int port = configured + 1; port < configured + 32 && port <= 65535; port++) {
            if (isPortFree(port)) {
                log.warn("embedding 端口 {} 已被占用，改用 {}", configured, port);
                return settingsService.save(settings.withEmbeddingLlamafilePort(port));
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
                + settings.embeddingLlamafilePort();
    }
}
