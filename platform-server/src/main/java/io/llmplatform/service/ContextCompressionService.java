package io.llmplatform.service;

import io.llmplatform.common.constant.ChatEventTypes;
import io.llmplatform.infra.llamafile.TokenCounter;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.pojo.vo.ModelConfigView;
import io.llmplatform.repository.ChatRepository;
import io.llmplatform.repository.entity.MessageEntity;
import io.llmplatform.repository.entity.SessionContextEntity;
import io.llmplatform.websocket.PlatformWebSocketHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 仅压缩当前会话：按 Token 占用率触发，摘要不能跨会话读取。 */
@Service
public class ContextCompressionService {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionService.class);

    private final ChatRepository chatRepository;
    private final TokenCounter tokenCounter;
    private final SessionSummarizer summarizer;
    private final PlatformWebSocketHandler webSocketHandler;

    public ContextCompressionService(
            ChatRepository chatRepository,
            TokenCounter tokenCounter,
            SessionSummarizer summarizer,
            PlatformWebSocketHandler webSocketHandler) {
        this.chatRepository = chatRepository;
        this.tokenCounter = tokenCounter;
        this.summarizer = summarizer;
        this.webSocketHandler = webSocketHandler;
    }

    /** 组装最终发送给模型的上下文。读取范围始终由 sessionId 限定，顺序固定为系统规则、已有摘要、 摘要水位之后的原始消息和本次输入，避免摘要内容与原始历史重复进入模型。 */
    public List<ChatMessage> buildHistory(
            String sessionId,
            String systemPrompt,
            List<ChatMessage> incoming,
            ModelConfigView config,
            String requestId,
            BiConsumer<String, Map<String, Object>> events) {
        int through = summarizedThrough(sessionId);
        List<MessageEntity> pending = chatRepository.findMessagesAfter(sessionId, through);
        String summary = currentSummary(sessionId);
        if (config.compressionEnabled()
                && exceedsTrigger(systemPrompt, summary, pending, incoming, config)) {
            pending = compress(sessionId, pending, summary, config, requestId, events);
            summary = currentSummary(sessionId);
        }
        List<ChatMessage> history = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            history.add(new ChatMessage("system", systemPrompt));
        }
        if (!summary.isBlank()) {
            history.add(new ChatMessage("system", "Session summary:\n" + summary));
        }
        for (MessageEntity message : pending) {
            history.add(new ChatMessage(message.getRole(), message.getContent()));
        }
        history.addAll(incoming);
        return history;
    }

    /** 同时受占用率和输出预留空间约束；两条线取较小值，防止尚未达到比例时已没有足够生成空间。 */
    int triggerLine(ModelConfigView config) {
        int byRatio = (int) Math.floor(config.contextSize() * config.compressionTriggerRatio());
        int reserved = Math.max(1, config.contextSize() - config.maxTokens());
        return Math.max(1, Math.min(byRatio, reserved));
    }

    private boolean exceedsTrigger(
            String systemPrompt,
            String summary,
            List<MessageEntity> pending,
            List<ChatMessage> incoming,
            ModelConfigView config) {
        return countPrompt(systemPrompt, summary, pending, incoming) > triggerLine(config);
    }

    private int countPrompt(
            String systemPrompt,
            String summary,
            List<MessageEntity> pending,
            List<ChatMessage> incoming) {
        int total = 0;
        total += countText(systemPrompt);
        total += countText(summary);
        for (MessageEntity message : pending) {
            total += countMessage(message);
        }
        for (ChatMessage message : incoming) {
            total += countText(message.content());
        }
        return total;
    }

    private List<MessageEntity> compress(
            String sessionId,
            List<MessageEntity> pending,
            String existingSummary,
            ModelConfigView config,
            String requestId,
            BiConsumer<String, Map<String, Object>> events) {
        int keep = Math.max(1, config.keepRecentMessages());
        List<MessageEntity> current = pending;
        String summary = existingSummary;
        // 防止异常配置或摘要无法降低占用时无限递归更新摘要。
        int guard = 0;
        while (current.size() > keep && guard++ < 8) {
            List<MessageEntity> old = current.subList(0, current.size() - keep);
            List<ChatMessage> toSummarize =
                    old.stream()
                            .map(item -> new ChatMessage(item.getRole(), item.getContent()))
                            .toList();
            int through = old.getLast().getSequenceNo();
            emit(
                    events,
                    requestId,
                    ChatEventTypes.COMPRESS_STARTED,
                    Map.of("sessionId", sessionId, "messageKey", "status.compressing"));
            try {
                String next = summarizer.summarize(summary, toSummarize, config);
                int summaryTokens = tokenCounter.count(next);
                chatRepository.upsertContext(
                        sessionId, next, through, summaryTokens, System.currentTimeMillis());
                summary = next;
                current = chatRepository.findMessagesAfter(sessionId, through);
                emit(
                        events,
                        requestId,
                        ChatEventTypes.COMPRESS_COMPLETED,
                        Map.of(
                                "sessionId",
                                sessionId,
                                "summarizedThroughSequence",
                                through,
                                "summaryTokenCount",
                                summaryTokens));
            } catch (Exception ex) {
                log.warn("会话摘要失败，保留最近消息继续对话");
                emit(
                        events,
                        requestId,
                        ChatEventTypes.COMPRESS_ERROR,
                        Map.of(
                                "sessionId",
                                sessionId,
                                "messageKey",
                                "error.contextCompressFailed"));
                // 摘要失败不能阻断对话；仅携带最近原始消息可控制预算，也不会从其它会话借用内容。
                return current.size() <= keep
                        ? current
                        : current.subList(current.size() - keep, current.size());
            }
        }
        return current;
    }

    private int summarizedThrough(String sessionId) {
        return chatRepository
                .findContext(sessionId)
                .map(SessionContextEntity::getSummarizedThroughSequence)
                .orElse(0);
    }

    private String currentSummary(String sessionId) {
        return chatRepository
                .findContext(sessionId)
                .map(SessionContextEntity::getSummary)
                .filter(text -> !text.isBlank())
                .orElse("");
    }

    private int countMessage(MessageEntity message) {
        if (message.getTokenCount() != null && message.getTokenCount() > 0) {
            return message.getTokenCount();
        }
        // 旧消息可能没有缓存计数，首次参与预算时补算并持久化，后续轮次直接复用。
        int counted = tokenCounter.count(message.getContent());
        if (counted > 0 && message.getId() != null) {
            chatRepository.updateTokenCount(message.getId(), counted);
        }
        return counted;
    }

    private int countText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return tokenCounter.count(text);
    }

    private void emit(
            BiConsumer<String, Map<String, Object>> events,
            String requestId,
            String type,
            Map<String, Object> payload) {
        // WebSocket 服务全局状态观察，SSE 服务当前请求；两条通道使用同一稳定事件契约。
        webSocketHandler.publish(type, requestId == null ? "" : requestId, payload);
        if (events != null) {
            events.accept(type, payload);
        }
    }
}
