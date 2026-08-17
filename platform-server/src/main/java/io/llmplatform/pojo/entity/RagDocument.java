package io.llmplatform.pojo.entity;

/** 本地知识库文档。 */
public record RagDocument(String id, String title, String filePath, int chunkCount) {}
