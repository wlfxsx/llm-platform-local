package io.llmplatform.pojo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** 应用级设置；模型运行参数已迁到每模型独立配置。 */
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
        boolean networkEnabled,
        /** local | remote；决定对话走 llamafile 还是远程 OpenAI 兼容接口。 */
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
    }

    /**
     * 推理硬件默认留空，由硬件探测按独显、核显、CPU 顺序回退，避免新机器默认只跑 CPU。
     *
     * <p>推理端口不用 llamafile 默认的 8080：该端口在本机极易被开发服务器或游戏客户端组件占用， 一旦被占，探活和启动都会受影响。
     */
    public static AppSettings defaults() {
        return new AppSettings(
                "zh", "system", "", "", 17891, "", "local", "", "local", "", "", 0, false, "local", "");
    }

    public boolean remoteChat() {
        return "remote".equalsIgnoreCase(chatProvider);
    }

    public AppSettings withLlamafilePort(int port) {
        return new AppSettings(
                language,
                theme,
                currentModelId,
                llamafileBinary,
                port,
                hardwareDeviceId,
                ragProvider,
                ragRemoteUrl,
                embeddingProvider,
                embeddingModel,
                embeddingBaseUrl,
                embeddingDimension,
                networkEnabled,
                chatProvider,
                currentRemoteModelId);
    }

    public AppSettings withCurrentModelId(String modelId) {
        return new AppSettings(
                language,
                theme,
                modelId,
                llamafileBinary,
                llamafilePort,
                hardwareDeviceId,
                ragProvider,
                ragRemoteUrl,
                embeddingProvider,
                embeddingModel,
                embeddingBaseUrl,
                embeddingDimension,
                networkEnabled,
                chatProvider,
                currentRemoteModelId);
    }

    public AppSettings withCurrentRemoteModelId(String remoteModelId) {
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
                networkEnabled,
                chatProvider,
                remoteModelId);
    }
}
