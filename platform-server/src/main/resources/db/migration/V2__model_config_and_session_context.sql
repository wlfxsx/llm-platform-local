-- 每模型独立参数与当前会话摘要。旧 memories 表如存在则保留但不读取。
-- 旧库的 messages/sessions 新列必须先按存在性检查再 ALTER，因此由 SchemaMigrator 条件补齐并回填；
-- 本脚本只创建全新表，消息序号索引也要等列存在后由 SchemaMigrator 创建。
CREATE TABLE IF NOT EXISTS model_configs (
    model_id TEXT PRIMARY KEY,
    context_size INTEGER NOT NULL,
    threads INTEGER NOT NULL,
    gpu_layers INTEGER NOT NULL,
    batch_size INTEGER NOT NULL,
    ubatch_size INTEGER NOT NULL,
    flash_attention INTEGER NOT NULL,
    memory_map INTEGER NOT NULL,
    memory_lock INTEGER NOT NULL,
    temperature REAL NOT NULL,
    top_p REAL NOT NULL,
    top_k INTEGER NOT NULL,
    min_p REAL NOT NULL,
    max_tokens INTEGER NOT NULL,
    repeat_penalty REAL NOT NULL,
    repeat_last_n INTEGER NOT NULL,
    seed INTEGER,
    frequency_penalty REAL NOT NULL,
    presence_penalty REAL NOT NULL,
    stop_json TEXT NOT NULL,
    compression_enabled INTEGER NOT NULL,
    compression_trigger_ratio REAL NOT NULL,
    keep_recent_messages INTEGER NOT NULL,
    summary_max_tokens INTEGER NOT NULL,
    advanced_inference_params TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (model_id) REFERENCES models (id)
);

CREATE TABLE IF NOT EXISTS session_contexts (
    session_id TEXT PRIMARY KEY,
    summary TEXT NOT NULL,
    summarized_through_sequence INTEGER NOT NULL,
    summary_token_count INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions (id)
);

-- 消息序号索引在列补齐后由 SchemaMigrator 创建。