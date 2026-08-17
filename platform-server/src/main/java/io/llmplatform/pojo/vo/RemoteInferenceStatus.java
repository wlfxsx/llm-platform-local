package io.llmplatform.pojo.vo;

/** 远程推理就绪状态。 */
public record RemoteInferenceStatus(
        String state,
        String messageKey,
        boolean ready,
        String remoteModelId,
        String remoteModelName) {}
