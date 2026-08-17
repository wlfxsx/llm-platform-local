-- “默认”预设由旧版的 CPU 层数 0 迁到自动卸载；用户自定义预设保留原值
UPDATE model_config_profiles
SET params_json = replace(params_json, '"gpuLayers":0', '"gpuLayers":999'),
    updated_at = CAST(strftime('%s', 'now') AS INTEGER) * 1000
WHERE name = '默认'
  AND params_json LIKE '%"gpuLayers":0%';
