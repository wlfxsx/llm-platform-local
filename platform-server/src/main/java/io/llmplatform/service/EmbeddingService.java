package io.llmplatform.service;

import io.llmplatform.infra.embedding.EmbeddingProvider;
import java.util.List;
import org.springframework.stereotype.Service;

/** 按设置选择本地或远程嵌入提供者。 */
@Service
public class EmbeddingService {

    private final SettingsService settingsService;
    private final List<EmbeddingProvider> providers;

    public EmbeddingService(SettingsService settingsService, List<EmbeddingProvider> providers) {
        this.settingsService = settingsService;
        this.providers = providers;
    }

    public EmbeddingProvider current() {
        String id = settingsService.current().embeddingProvider();
        return providers.stream()
                .filter(provider -> provider.id().equals(id))
                .findFirst()
                .orElseGet(
                        () ->
                                providers.stream()
                                        .filter(provider -> "local".equals(provider.id()))
                                        .findFirst()
                                        .orElseThrow());
    }
}
