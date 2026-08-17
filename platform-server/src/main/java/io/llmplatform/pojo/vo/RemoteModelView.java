package io.llmplatform.pojo.vo;

/** 远程模型配置视图；永不回显明文 API Key。 */
public record RemoteModelView(
        String id,
        String name,
        String baseUrl,
        String modelName,
        boolean hasApiKey,
        long createdAt,
        long updatedAt) {}
