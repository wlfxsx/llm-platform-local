package io.llmplatform.pojo.vo;

/** 平台就绪状态：含本地进程、辅助 embedding/rerank、远程配置与向量扩展。 */
public record PlatformStatus(
        LlamafileStatus llamafile,
        LlamafileStatus embedding,
        LlamafileStatus rerank,
        RemoteInferenceStatus remote,
        String chatProvider,
        boolean inferenceReady,
        boolean vectorExtensionLoaded,
        String vectorExtensionVersion,
        boolean embeddingModelPresent,
        boolean rerankModelPresent) {}
