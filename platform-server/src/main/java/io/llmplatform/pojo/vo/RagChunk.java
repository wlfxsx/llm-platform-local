package io.llmplatform.pojo.vo;

/** 检索命中的文档片段。 */
public record RagChunk(
        String id,
        String documentId,
        String content,
        double score,
        String title,
        String headingPath) {

    public RagChunk(String id, String documentId, String content, double score) {
        this(id, documentId, content, score, "", "");
    }
}
