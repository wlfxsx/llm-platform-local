package io.llmplatform.service;

import io.llmplatform.common.constant.CapabilityIds;
import io.llmplatform.infra.rag.LocalRagProvider;
import io.llmplatform.infra.rag.RagProvider;
import io.llmplatform.pojo.vo.RagChunk;
import java.util.List;
import org.springframework.stereotype.Service;

/** 按设置选择 RAG 提供者。 */
@Service
public class RagService {

    private final SettingsService settingsService;
    private final CapabilityService capabilities;
    private final List<RagProvider> providers;
    private final LocalRagProvider localRagProvider;

    public RagService(
            SettingsService settingsService,
            CapabilityService capabilities,
            List<RagProvider> providers,
            LocalRagProvider localRagProvider) {
        this.settingsService = settingsService;
        this.capabilities = capabilities;
        this.providers = providers;
        this.localRagProvider = localRagProvider;
    }

    public LocalRagProvider local() {
        return localRagProvider;
    }

    public String retrieve(String query, String sessionId) {
        if (!capabilities.isEnabledForSession(sessionId, CapabilityIds.RAG)) {
            return "";
        }
        String id = settingsService.current().ragProvider();
        RagProvider provider =
                providers.stream()
                        .filter(item -> item.id().equals(id))
                        .findFirst()
                        .orElse(localRagProvider);
        StringBuilder builder = new StringBuilder();
        for (RagChunk chunk : provider.retrieve(query, 5)) {
            builder.append(chunk.content()).append('\n');
        }
        return builder.toString();
    }
}
