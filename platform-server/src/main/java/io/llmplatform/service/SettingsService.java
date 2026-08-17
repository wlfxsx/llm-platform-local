package io.llmplatform.service;

import io.llmplatform.infra.agentscope.AgentScopeRuntimeAdapter;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.repository.SettingsRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** 读取和更新应用设置。 */
@Service
public class SettingsService {

    private final SettingsRepository repository;
    private final AgentScopeRuntimeAdapter agentScopeRuntimeAdapter;

    public SettingsService(
            SettingsRepository repository,
            @Lazy AgentScopeRuntimeAdapter agentScopeRuntimeAdapter) {
        this.repository = repository;
        this.agentScopeRuntimeAdapter = agentScopeRuntimeAdapter;
    }

    public AppSettings current() {
        return repository.load();
    }

    public AppSettings save(AppSettings settings) {
        AppSettings previous = repository.load();
        String provider =
                settings.chatProvider() == null || settings.chatProvider().isBlank()
                        ? "local"
                        : settings.chatProvider().trim().toLowerCase();
        if (!"local".equals(provider) && !"remote".equals(provider)) {
            provider = "local";
        }
        AppSettings normalized =
                new AppSettings(
                        settings.language(),
                        settings.theme(),
                        settings.currentModelId(),
                        settings.llamafileBinary(),
                        settings.llamafilePort(),
                        settings.hardwareDeviceId(),
                        settings.ragProvider(),
                        settings.ragRemoteUrl(),
                        settings.embeddingProvider(),
                        settings.embeddingModel(),
                        settings.embeddingBaseUrl(),
                        settings.embeddingDimension(),
                        settings.networkEnabled(),
                        provider,
                        settings.currentRemoteModelId() == null
                                ? ""
                                : settings.currentRemoteModelId());
        repository.save(normalized);
        if (!previous.chatProvider().equalsIgnoreCase(normalized.chatProvider())
                || !previous.currentRemoteModelId().equals(normalized.currentRemoteModelId())) {
            // 切换来源或远程配置后重建 AgentScope，避免继续打旧 endpoint。
            agentScopeRuntimeAdapter.reset();
        }
        return normalized;
    }
}
