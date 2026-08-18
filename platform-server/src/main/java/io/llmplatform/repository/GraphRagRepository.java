package io.llmplatform.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** GraphRAG 实体与边，全部在本机 SQLite。 */
@Repository
public class GraphRagRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;

    public GraphRagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) {
            return false;
        }
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static EntityRow mapEntity(ResultSet rs) throws SQLException {
        return new EntityRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                rs.getString("type"),
                rs.getString("document_id"));
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
        return namedJdbc.query(
                "SELECT id, name, normalized_name, type, document_id FROM rag_entities"
                        + " WHERE normalized_name IN (:names)",
                new MapSqlParameterSource("names", names),
                (rs, rowNum) -> mapEntity(rs));
    }

    public List<EntityRow> findEntitiesByIds(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return namedJdbc.query(
                "SELECT id, name, normalized_name, type, document_id FROM rag_entities"
                        + " WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids),
                (rs, rowNum) -> mapEntity(rs));
    }

    public List<EntityRow> findEntitiesMentioned(String query) {
        List<EntityRow> rows = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id, name, normalized_name, type, document_id FROM rag_entities",
                rs -> {
                    String name = rs.getString("name");
                    String normalized = rs.getString("normalized_name");
                    if (containsIgnoreCase(query, name) || containsIgnoreCase(query, normalized)) {
                        rows.add(mapEntity(rs));
                    }
                });
        return rows;
    }

    public List<EdgeRow> findEdgesTouching(List<String> entityIds) {
        if (entityIds.isEmpty()) {
            return List.of();
        }
        return namedJdbc.query(
                "SELECT id, from_id, to_id, predicate, evidence_chunk_id, document_id FROM rag_edges"
                        + " WHERE from_id IN (:ids) OR to_id IN (:ids)",
                new MapSqlParameterSource("ids", entityIds),
                (rs, rowNum) ->
                        new EdgeRow(
                                rs.getString("id"),
                                rs.getString("from_id"),
                                rs.getString("to_id"),
                                rs.getString("predicate"),
                                rs.getString("evidence_chunk_id"),
                                rs.getString("document_id")));
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
}
