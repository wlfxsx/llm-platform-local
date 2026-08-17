package io.llmplatform.pojo.vo;

/** 可命名的完整模型参数策略。 */
public record ModelConfigProfileView(
        String id, String name, ModelConfigView config, long createdAt, long updatedAt) {}
