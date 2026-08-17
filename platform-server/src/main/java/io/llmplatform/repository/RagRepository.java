package io.llmplatform.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.llmplatform.pojo.entity.RagDocument;
import io.llmplatform.repository.convert.EntityConverters;
import io.llmplatform.repository.entity.RagChunkEntity;
import io.llmplatform.repository.entity.RagDocumentEntity;
import io.llmplatform.repository.mapper.RagChunkMapper;
import io.llmplatform.repository.mapper.RagDocumentMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 本地知识库文档与切片的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class RagRepository {

    private final RagDocumentMapper documentMapper;
    private final RagChunkMapper chunkMapper;
    private final EntityConverters converters;

    public List<RagDocument> findAllDocuments() {
        return documentMapper
                .selectList(
                        Wrappers.<RagDocumentEntity>lambdaQuery()
                                .orderByDesc(RagDocumentEntity::getImportedAt))
                .stream()
                .map(converters::toRagDocument)
                .toList();
    }

    @Transactional
    public void insertDocument(RagDocument document, long importedAt) {
        RagDocumentEntity entity = converters.toRagEntity(document);
        entity.setImportedAt(importedAt);
        documentMapper.insert(entity);
    }

    @Transactional
    public void insertChunk(String id, String documentId, String content) {
        RagChunkEntity entity = new RagChunkEntity();
        entity.setId(id);
        entity.setDocumentId(documentId);
        entity.setContent(content);
        chunkMapper.insert(entity);
    }

    @Transactional
    public void deleteDocument(String id) {
        chunkMapper.delete(
                Wrappers.<RagChunkEntity>lambdaQuery().eq(RagChunkEntity::getDocumentId, id));
        documentMapper.deleteById(id);
    }
}
