package io.llmplatform.infra.rag;

import io.llmplatform.common.constant.CapabilityIds;
import io.llmplatform.service.CapabilityService;
import io.llmplatform.service.RemoteModelService;
import io.llmplatform.service.SettingsService;
import org.springframework.stereotype.Component;

/** GraphRAG / HyDE 仅在远程大模型就绪时放行，避免本地弱模型抽图或编假设文档。 */
@Component
public class RemoteEnhancementGate {

    private final CapabilityService capabilities;
    private final SettingsService settingsService;
    private final RemoteModelService remoteModelService;

    public RemoteEnhancementGate(
            CapabilityService capabilities,
            SettingsService settingsService,
            RemoteModelService remoteModelService) {
        this.capabilities = capabilities;
        this.settingsService = settingsService;
        this.remoteModelService = remoteModelService;
    }

    public boolean graphAllowed(String sessionId) {
        return allowed(sessionId, CapabilityIds.GRAPH_RAG);
    }

    public boolean hydeAllowed(String sessionId) {
        return allowed(sessionId, CapabilityIds.HYDE);
    }

    public boolean remoteReady() {
        return settingsService.current().remoteChat()
                && settingsService.current().networkEnabled()
                && remoteModelService.currentReady();
    }

    private boolean allowed(String sessionId, String capabilityId) {
        if (!remoteReady()) {
            return false;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return capabilities.isEnabled(capabilityId);
        }
        return capabilities.isEnabledForSession(sessionId, capabilityId);
    }
}
