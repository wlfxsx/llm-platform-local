package io.llmplatform.pojo.vo;

/** 检索命中的文档片段。 */
public record RagChunk(String id, String documentId, String content, double score) {}
