package io.llmplatform.infra.rag;

import io.llmplatform.pojo.vo.RagChunk;
import java.util.List;

/** 本地或远程 RAG 提供者。 */
public interface RagProvider {

    String id();

    List<RagChunk> retrieve(String query, int topK);

    default List<RagChunk> retrieve(String query, int topK, String sessionId) {
        return retrieve(query, topK);
    }
}
