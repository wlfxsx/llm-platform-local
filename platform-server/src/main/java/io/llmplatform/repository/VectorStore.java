package io.llmplatform.repository;

import io.llmplatform.pojo.vo.VectorHit;
import java.util.List;

/** 可替换的向量存储。业务层不得依赖具体 SQL 扩展方言。 */
public interface VectorStore {

    void upsert(String collection, String id, float[] embedding, String payload);

    List<VectorHit> search(String collection, float[] embedding, int topK);

    void delete(String collection, String id);

    default void clear(String collection) {
        // 默认空操作：调用方可以只实现检索，不必强制支持整库清空。
    }
}
