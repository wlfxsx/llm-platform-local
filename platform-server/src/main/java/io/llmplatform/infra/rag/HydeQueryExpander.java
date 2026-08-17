package io.llmplatform.infra.rag;

import io.llmplatform.infra.llm.OpenAiCompatibleChatClient;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.service.ModelConfigService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 远程 HyDE：生成假设答案文档供向量检索；失败则返回空，由调用方回退原问题。 */
@Component
public class HydeQueryExpander {

    private static final Logger log = LoggerFactory.getLogger(HydeQueryExpander.class);
    private static final int MAX_CHARS = 800;
    private final RemoteEnhancementGate gate;
    private final OpenAiCompatibleChatClient remoteChat;
    private final ModelConfigService modelConfigService;

    public HydeQueryExpander(
            RemoteEnhancementGate gate,
            OpenAiCompatibleChatClient remoteChat,
            ModelConfigService modelConfigService) {
        this.gate = gate;
        this.remoteChat = remoteChat;
        this.modelConfigService = modelConfigService;
    }

    public String expand(String query, String sessionId) {
        if (query == null || query.isBlank() || !gate.hydeAllowed(sessionId)) {
            return "";
        }
        try {
            List<ChatMessage> messages =
                    List.of(
                            new ChatMessage(
                                    "system",
                                    "Write a short hypothetical answer passage that could appear in a knowledge base. No preamble."),
                            new ChatMessage("user", query));
            String text =
                    remoteChat.completeNonStream(
                            messages, modelConfigService.currentForInference(), 0.2, 256);
            if (text == null) {
                return "";
            }
            String trimmed = text.trim();
            return trimmed.length() > MAX_CHARS ? trimmed.substring(0, MAX_CHARS) : trimmed;
        } catch (RuntimeException ex) {
            log.info("HyDE 生成失败，回退原问题向量检索");
            return "";
        }
    }
}
