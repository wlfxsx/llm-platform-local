package io.llmplatform.infra.rag;

/** 检索流水线内部命中，随后再格式化注入。 */
public record RetrievedChunk(
        String id,
        String documentId,
        String content,
        double score,
        String title,
        String headingPath) {}
