package io.llmplatform.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.repository.mapper.SettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 以单行 JSON 保存应用级设置，模型参数已迁到 model_configs。 */
@Repository
@RequiredArgsConstructor
public class SettingsRepository {

    private static final String KEY = "app.settings";
    private final SettingMapper settingMapper;
    private final ObjectMapper objectMapper;

    public AppSettings load() {
        try {
            String json = settingMapper.selectValue(KEY);
            if (json == null || json.isBlank()) {
                return AppSettings.defaults();
            }
            return objectMapper.readValue(json, AppSettings.class);
        } catch (Exception ex) {
            return AppSettings.defaults();
        }
    }

    public void save(AppSettings settings) {
        try {
            String json = objectMapper.writeValueAsString(settings);
            settingMapper.upsertValue(KEY, json);
        } catch (Exception ex) {
            throw new PlatformException("INTERNAL", "error.internal");
        }
    }
}
