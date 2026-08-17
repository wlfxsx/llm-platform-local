-- GraphRAG 实体与边仍落在本机 SQLite，不引入外部图数据库。

CREATE TABLE IF NOT EXISTS rag_entities (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    type TEXT NOT NULL,
    document_id TEXT,
    embedding BLOB
);

CREATE INDEX IF NOT EXISTS idx_rag_entities_normalized ON rag_entities (normalized_name);
CREATE INDEX IF NOT EXISTS idx_rag_entities_document ON rag_entities (document_id);

CREATE TABLE IF NOT EXISTS rag_edges (
    id TEXT PRIMARY KEY,
    from_id TEXT NOT NULL,
    to_id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    evidence_chunk_id TEXT,
    document_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rag_edges_from ON rag_edges (from_id);
CREATE INDEX IF NOT EXISTS idx_rag_edges_to ON rag_edges (to_id);
CREATE INDEX IF NOT EXISTS idx_rag_edges_document ON rag_edges (document_id);

CREATE TABLE IF NOT EXISTS rag_entity_chunks (
    entity_id TEXT NOT NULL,
    chunk_id TEXT NOT NULL,
    PRIMARY KEY (entity_id, chunk_id)
);
