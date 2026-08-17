package io.llmplatform.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.llmplatform.pojo.entity.RagDocument;
import io.llmplatform.repository.convert.EntityConverters;
import io.llmplatform.repository.entity.RagChunkEntity;
import io.llmplatform.repository.entity.RagDocumentEntity;
import io.llmplatform.repository.mapper.RagChunkMapper;
import io.llmplatform.repository.mapper.RagDocumentMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 本地知识库文档与切片的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class RagRepository {

    private final RagDocumentMapper documentMapper;
    private final RagChunkMapper chunkMapper;
    private final EntityConverters converters;
    private final JdbcTemplate jdbcTemplate;

    public List<RagDocument> findAllDocuments() {
        return documentMapper
                .selectList(
                        Wrappers.<RagDocumentEntity>lambdaQuery()
                                .orderByDesc(RagDocumentEntity::getImportedAt))
                .stream()
                .map(converters::toRagDocument)
                .toList();
    }

    public Optional<RagDocumentEntity> findDocument(String id) {
        return Optional.ofNullable(documentMapper.selectById(id));
    }

    @Transactional
    public void insertDocument(RagDocument document, long importedAt) {
        RagDocumentEntity entity = converters.toRagEntity(document);
        entity.setImportedAt(importedAt);
        documentMapper.insert(entity);
    }

    @Transactional
    public void insertChunk(RagChunkEntity entity) {
        chunkMapper.insert(entity);
        if ("child".equals(entity.getRole())) {
            jdbcTemplate.update(
                    "INSERT INTO rag_chunks_fts(chunk_id, content, heading_path) VALUES(?, ?, ?)",
                    entity.getId(),
                    entity.getContent(),
                    entity.getHeadingPath());
        }
    }

    public Optional<RagChunkEntity> findChunk(String id) {
        return Optional.ofNullable(chunkMapper.selectById(id));
    }

    public List<RagChunkEntity> findChildren(String documentId) {
        return chunkMapper.selectList(
                Wrappers.<RagChunkEntity>lambdaQuery()
                        .eq(RagChunkEntity::getDocumentId, documentId)
                        .eq(RagChunkEntity::getRole, "child")
                        .orderByAsc(RagChunkEntity::getOrdinal));
    }

    public List<RagChunkEntity> findParents(String documentId) {
        return chunkMapper.selectList(
                Wrappers.<RagChunkEntity>lambdaQuery()
                        .eq(RagChunkEntity::getDocumentId, documentId)
                        .eq(RagChunkEntity::getRole, "parent")
                        .orderByAsc(RagChunkEntity::getOrdinal));
    }

    public List<String> searchFts(String matchQuery, int limit) {
        if (matchQuery == null || matchQuery.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT chunk_id FROM rag_chunks_fts WHERE rag_chunks_fts MATCH ? ORDER BY rank LIMIT ?",
                rs -> {
                    ids.add(rs.getString(1));
                },
                matchQuery,
                limit);
        return ids;
    }

    /** 已有向量的字节长度；用于禁止 64 维 hash 与 1024 维 BGE 混用。 */
    public int storedEmbeddingByteLength() {
        Integer length =
                jdbcTemplate.query(
                        "SELECT length(embedding) FROM rag_chunks WHERE embedding IS NOT NULL LIMIT 1",
                        rs -> rs.next() ? rs.getInt(1) : 0);
        return length == null ? 0 : length;
    }

    @Transactional
    public void deleteDocument(String id) {
        List<RagChunkEntity> chunks =
                chunkMapper.selectList(
                        Wrappers.<RagChunkEntity>lambdaQuery()
                                .eq(RagChunkEntity::getDocumentId, id));
        for (RagChunkEntity chunk : chunks) {
            jdbcTemplate.update("DELETE FROM rag_chunks_fts WHERE chunk_id = ?", chunk.getId());
        }
        chunkMapper.delete(
                Wrappers.<RagChunkEntity>lambdaQuery().eq(RagChunkEntity::getDocumentId, id));
        documentMapper.deleteById(id);
    }
}
