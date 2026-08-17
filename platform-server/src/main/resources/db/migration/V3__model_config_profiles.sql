-- 用户自定义的完整模型参数策略，与具体模型解耦，可应用到任意模型编辑表单。
CREATE TABLE IF NOT EXISTS model_config_profiles (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    name_normalized TEXT NOT NULL UNIQUE,
    params_json TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
