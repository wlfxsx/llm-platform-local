package io.llmplatform.repository;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** GraphRAG 实体与边，全部在本机 SQLite。 */
@Repository
public class GraphRagRepository {

    private final JdbcTemplate jdbcTemplate;

    public GraphRagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void deleteByDocument(String documentId) {
        jdbcTemplate.update(
                "DELETE FROM rag_entity_chunks WHERE entity_id IN (SELECT id FROM rag_entities WHERE document_id = ?)",
                documentId);
        jdbcTemplate.update("DELETE FROM rag_edges WHERE document_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM rag_entities WHERE document_id = ?", documentId);
    }

    @Transactional
    public void insertEntity(
            String id, String name, String normalized, String type, String documentId) {
        jdbcTemplate.update(
                "INSERT INTO rag_entities(id, name, normalized_name, type, document_id) VALUES(?, ?, ?, ?, ?)",
                id,
                name,
                normalized,
                type,
                documentId);
    }

    @Transactional
    public void insertEdge(
            String id,
            String fromId,
            String toId,
            String predicate,
            String evidenceChunkId,
            String documentId) {
        jdbcTemplate.update(
                "INSERT INTO rag_edges(id, from_id, to_id, predicate, evidence_chunk_id, document_id)"
                        + " VALUES(?, ?, ?, ?, ?, ?)",
                id,
                fromId,
                toId,
                predicate,
                evidenceChunkId,
                documentId);
    }

    @Transactional
    public void insertEntityChunk(String entityId, String chunkId) {
        jdbcTemplate.update(
                "INSERT OR IGNORE INTO rag_entity_chunks(entity_id, chunk_id) VALUES(?, ?)",
                entityId,
                chunkId);
    }

    public String findId(String documentId, String normalized) {
        if (documentId == null || normalized == null || normalized.isBlank()) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id FROM rag_entities WHERE document_id = ? AND normalized_name = ? LIMIT 1",
                rs -> {
                    ids.add(rs.getString(1));
                },
                documentId,
                normalized);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public List<EntityRow> findEntitiesByNormalized(List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", names.stream().map(item -> "?").toList());
        List<EntityRow> rows = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id, name, normalized_name, type, document_id FROM rag_entities WHERE normalized_name IN ("
                        + placeholders
                        + ")",
                rs -> {
                    rows.add(
                            new EntityRow(
                                    rs.getString("id"),
                                    rs.getString("name"),
                                    rs.getString("normalized_name"),
                                    rs.getString("type"),
                                    rs.getString("document_id")));
                },
                names.toArray());
        return rows;
    }

    public List<EntityRow> findEntitiesByIds(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(item -> "?").toList());
        List<EntityRow> rows = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id, name, normalized_name, type, document_id FROM rag_entities WHERE id IN ("
                        + placeholders
                        + ")",
                rs -> {
                    rows.add(
                            new EntityRow(
                                    rs.getString("id"),
                                    rs.getString("name"),
                                    rs.getString("normalized_name"),
                                    rs.getString("type"),
                                    rs.getString("document_id")));
                },
                ids.toArray());
        return rows;
    }

    public List<EntityRow> findEntitiesMentioned(String query) {
        List<EntityRow> rows = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id, name, normalized_name, type, document_id FROM rag_entities",
                rs -> {
                    String name = rs.getString("name");
                    String normalized = rs.getString("normalized_name");
                    if (containsIgnoreCase(query, name) || containsIgnoreCase(query, normalized)) {
                        rows.add(
                                new EntityRow(
                                        rs.getString("id"),
                                        name,
                                        normalized,
                                        rs.getString("type"),
                                        rs.getString("document_id")));
                    }
                });
        return rows;
    }

    public List<EdgeRow> findEdgesTouching(List<String> entityIds) {
        if (entityIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", entityIds.stream().map(item -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.addAll(entityIds);
        args.addAll(entityIds);
        List<EdgeRow> rows = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id, from_id, to_id, predicate, evidence_chunk_id, document_id FROM rag_edges"
                        + " WHERE from_id IN ("
                        + placeholders
                        + ") OR to_id IN ("
                        + placeholders
                        + ")",
                rs -> {
                    rows.add(
                            new EdgeRow(
                                    rs.getString("id"),
                                    rs.getString("from_id"),
                                    rs.getString("to_id"),
                                    rs.getString("predicate"),
                                    rs.getString("evidence_chunk_id"),
                                    rs.getString("document_id")));
                },
                args.toArray());
        return rows;
    }

    public record EntityRow(
            String id, String name, String normalizedName, String type, String documentId) {}

    public record EdgeRow(
            String id,
            String fromId,
            String toId,
            String predicate,
            String evidenceChunkId,
            String documentId) {}

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) {
            return false;
        }
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
