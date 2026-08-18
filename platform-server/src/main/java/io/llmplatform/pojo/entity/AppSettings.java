package io.llmplatform.pojo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 应用级设置；模型运行参数已迁到每模型独立配置。
 *
 * <p>{@code chatProvider} 为 local 或 remote，决定对话走 llamafile 还是远程 OpenAI 兼容接口。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppSettings(
        @NotBlank String language,
        @NotBlank String theme,
        String currentModelId,
        String llamafileBinary,
        @Min(1) @Max(65535) int llamafilePort,
        String hardwareDeviceId,
        String ragProvider,
        String ragRemoteUrl,
        String embeddingProvider,
        String embeddingModel,
        String embeddingBaseUrl,
        @Min(0) int embeddingDimension,
        @Min(0) @Max(65535) int embeddingLlamafilePort,
        @Min(0) @Max(65535) int rerankLlamafilePort,
        boolean networkEnabled,
        String chatProvider,
        String currentRemoteModelId) {

    public AppSettings {
        if (chatProvider == null || chatProvider.isBlank()) {
            chatProvider = "local";
        }
        if (currentRemoteModelId == null) {
            currentRemoteModelId = "";
        }
        if (currentModelId == null) {
            currentModelId = "";
        }
        if (hardwareDeviceId == null) {
            hardwareDeviceId = "";
        }
        if (embeddingProvider == null || embeddingProvider.isBlank()) {
            embeddingProvider = "local";
        }
        if (embeddingLlamafilePort <= 0) {
            embeddingLlamafilePort = 17892;
        }
        if (rerankLlamafilePort <= 0) {
            rerankLlamafilePort = 17893;
        }
        if (embeddingDimension <= 0) {
            embeddingDimension = 1024;
        }
    }

    /**
     * 推理硬件默认留空，由硬件探测按独显、核显、CPU 顺序回退，避免新机器默认只跑 CPU。
     *
     * <p>推理端口不用 llamafile 默认的 8080：该端口在本机极易被开发服务器或游戏客户端组件占用， 一旦被占，探活和启动都会受影响。
     */
    public static AppSettings defaults() {
        return new AppSettings(
                "zh", "system", "", "", 17891, "", "local", "", "local", "", "", 1024, 17892, 17893,
                false, "local", "");
    }

    public boolean remoteChat() {
        return "remote".equalsIgnoreCase(chatProvider);
    }

    public AppSettings withLlamafilePort(int port) {
        return copy(
                llamafileBinary,
                port,
                embeddingLlamafilePort,
                rerankLlamafilePort,
                currentModelId,
                currentRemoteModelId,
                networkEnabled);
    }

    public AppSettings withEmbeddingLlamafilePort(int port) {
        return copy(
                llamafileBinary,
                llamafilePort,
                port,
                rerankLlamafilePort,
                currentModelId,
                currentRemoteModelId,
                networkEnabled);
    }

    public AppSettings withRerankLlamafilePort(int port) {
        return copy(
                llamafileBinary,
                llamafilePort,
                embeddingLlamafilePort,
                port,
                currentModelId,
                currentRemoteModelId,
                networkEnabled);
    }

    public AppSettings withCurrentModelId(String modelId) {
        return copy(
                llamafileBinary,
                llamafilePort,
                embeddingLlamafilePort,
                rerankLlamafilePort,
                modelId,
                currentRemoteModelId,
                networkEnabled);
    }

    public AppSettings withCurrentRemoteModelId(String remoteModelId) {
        return copy(
                llamafileBinary,
                llamafilePort,
                embeddingLlamafilePort,
                rerankLlamafilePort,
                currentModelId,
                remoteModelId,
                networkEnabled);
    }

    public AppSettings withChatProvider(String provider, String remoteModelId) {
        return new AppSettings(
                language,
                theme,
                currentModelId,
                llamafileBinary,
                llamafilePort,
                hardwareDeviceId,
                ragProvider,
                ragRemoteUrl,
                embeddingProvider,
                embeddingModel,
                embeddingBaseUrl,
                embeddingDimension,
                embeddingLlamafilePort,
                rerankLlamafilePort,
                networkEnabled,
                provider,
                remoteModelId);
    }

    public AppSettings withNetworkEnabled(boolean enabled) {
        return copy(
                llamafileBinary,
                llamafilePort,
                embeddingLlamafilePort,
                rerankLlamafilePort,
                currentModelId,
                currentRemoteModelId,
                enabled);
    }

    private AppSettings copy(
            String binary,
            int chatPort,
            int embeddingPort,
            int rerankPort,
            String modelId,
            String remoteModelId,
            boolean network) {
        return new AppSettings(
                language,
                theme,
                modelId,
                binary,
                chatPort,
                hardwareDeviceId,
                ragProvider,
                ragRemoteUrl,
                embeddingProvider,
                embeddingModel,
                embeddingBaseUrl,
                embeddingDimension,
                embeddingPort,
                rerankPort,
                network,
                chatProvider,
                remoteModelId);
    }
}
