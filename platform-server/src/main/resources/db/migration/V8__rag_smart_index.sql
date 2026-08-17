-- 子块用于检索，父块用于注入；FTS 与向量只索引子块。
-- 不使用 content= 触发器：迁移切分器不能安全解析 BEGIN/END 内分号。

CREATE VIRTUAL TABLE IF NOT EXISTS rag_chunks_fts USING fts5(
    chunk_id UNINDEXED,
    content,
    heading_path,
    tokenize = 'unicode61'
);
