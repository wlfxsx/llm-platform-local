package io.llmplatform.pojo.vo;

/** 平台就绪状态：含本地进程、远程配置与向量扩展。 */
public record PlatformStatus(
        LlamafileStatus llamafile,
        RemoteInferenceStatus remote,
        String chatProvider,
        boolean inferenceReady,
        boolean vectorExtensionLoaded,
        String vectorExtensionVersion) {}
