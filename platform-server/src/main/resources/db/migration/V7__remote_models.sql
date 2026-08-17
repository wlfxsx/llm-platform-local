-- 远程 OpenAI 兼容模型配置；密钥只存凭据库引用，不落明文。
CREATE TABLE IF NOT EXISTS remote_models (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    name_normalized TEXT NOT NULL UNIQUE,
    base_url TEXT NOT NULL,
    model_name TEXT NOT NULL,
    secret_ref TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
