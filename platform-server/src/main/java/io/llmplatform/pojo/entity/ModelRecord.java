package io.llmplatform.pojo.entity;

/** 已导入的本地模型。 */
public record ModelRecord(String id, String name, String filePath, long importedAt) {}
