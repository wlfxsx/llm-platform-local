package io.llmplatform.service;

import io.llmplatform.common.constant.CapabilityIds;
import io.llmplatform.infra.agentscope.AgentScopeRuntimeAdapter;
import io.llmplatform.infra.llamafile.TokenCounter;
import io.llmplatform.infra.llm.ChatModelClient;
import io.llmplatform.infra.monitor.InferenceThroughputTracker;
import io.llmplatform.infra.rag.RagContextFormatter;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.pojo.dto.ChatRequest;
import io.llmplatform.pojo.vo.ChatResponse;
import io.llmplatform.pojo.vo.ModelConfigView;
import io.llmplatform.pojo.vo.PlatformTool;
import io.llmplatform.pojo.vo.RagChunk;
import io.llmplatform.repository.ChatRepository;
import io.llmplatform.websocket.PlatformWebSocketHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** 对话编排：仅加载当前会话历史，必要时摘要压缩后再调用本地模型。 */
@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final SkillService skillService;
    private final RagService ragService;
    private final CapabilityService capabilityService;
    private final ChatModelClient chatClient;
    private final AgentScopeRuntimeAdapter agentScopeRuntimeAdapter;
    private final PlatformWebSocketHandler webSocketHandler;
    private final ToolService toolService;
    private final ModelConfigService modelConfigService;
    private final ContextCompressionService compressionService;
    private final TokenCounter tokenCounter;
    private final InferenceThroughputTracker throughputTracker;

    public ChatService(
            ChatRepository chatRepository,
            SkillService skillService,
            RagService ragService,
            CapabilityService capabilityService,
            ChatModelClient chatClient,
            AgentScopeRuntimeAdapter agentScopeRuntimeAdapter,
            PlatformWebSocketHandler webSocketHandler,
            ToolService toolService,
            ModelConfigService modelConfigService,
            ContextCompressionService compressionService,
            TokenCounter tokenCounter,
            InferenceThroughputTracker throughputTracker) {
        this.chatRepository = chatRepository;
        this.skillService = skillService;
        this.ragService = ragService;
        this.capabilityService = capabilityService;
        this.chatClient = chatClient;
        this.agentScopeRuntimeAdapter = agentScopeRuntimeAdapter;
        this.webSocketHandler = webSocketHandler;
        this.toolService = toolService;
        this.modelConfigService = modelConfigService;
        this.compressionService = compressionService;
        this.tokenCounter = tokenCounter;
        this.throughputTracker = throughputTracker;
    }

    public ChatResponse complete(ChatRequest request) {
        String sessionId = ensureSession(request);
        String content =
                chatClient.complete(prepare(sessionId, request.messages(), sessionId, null));
        saveMessages(sessionId, request.messages(), content);
        return new ChatResponse(sessionId, content);
    }

    /** 流式响应同时累计完整助手消息，结束后再持久化，避免把多个增量片段误当成多条历史消息。 */
    public String stream(
            ChatRequest request,
            String requestId,
            Consumer<String> onDelta,
            BiConsumer<String, Map<String, Object>> onEvent) {
        String sessionId = ensureSession(request);
        agentScopeRuntimeAdapter.chatModel();
        StringBuilder builder = new StringBuilder();
        webSocketHandler.publish("status", requestId, Map.of("state", "generating"));
        // 生成过程本身就是吞吐样本；llamafile 版本缺少 /metrics 时监控页仍有数据可用。
        try (InferenceThroughputTracker.Generation generation = throughputTracker.begin()) {
            chatClient.stream(
                    prepare(sessionId, request.messages(), requestId, onEvent),
                    delta -> {
                        builder.append(delta);
                        generation.onDelta();
                        onDelta.accept(delta);
                    });
        }
        saveMessages(sessionId, request.messages(), builder.toString());
        webSocketHandler.publish("chat.done", requestId, Map.of("sessionId", sessionId));
        return sessionId;
    }

    private List<ChatMessage> prepare(
            String sessionId,
            List<ChatMessage> messages,
            String requestId,
            BiConsumer<String, Map<String, Object>> onEvent) {
        List<ChatMessage> source = messages == null ? List.of() : messages;
        StringBuilder system = new StringBuilder();
        system.append(skillService.promptFor(sessionId));
        String lastUser = lastUser(source);
        List<RagChunk> ragChunks = ragService.retrieveChunks(lastUser, sessionId);
        String rag = RagContextFormatter.format(ragChunks);
        if (!rag.isBlank()) {
            system.append("\nRAG:\n").append(rag);
            webSocketHandler.publish(
                    "rag.hit",
                    sessionId,
                    Map.of(
                            "chars",
                            rag.length(),
                            "hits",
                            ragChunks.size(),
                            "titles",
                            RagContextFormatter.titles(ragChunks)));
        }
        for (PlatformTool tool : toolService.enabledFor(sessionId)) {
            system.append("\nTool: ").append(tool.name()).append(" - ").append(tool.description());
        }
        // 关闭会话上下文时仍保留本轮系统规则、RAG 和工具说明，但不读取任何历史或摘要。
        if (!capabilityService.isEnabledForSession(sessionId, CapabilityIds.CONTEXT)) {
            List<ChatMessage> prepared = new ArrayList<>();
            if (!system.isEmpty()) {
                prepared.add(new ChatMessage("system", system.toString()));
            }
            prepared.addAll(source);
            return prepared;
        }
        ModelConfigView config = modelConfigService.currentForInference();
        return compressionService.buildHistory(
                sessionId, system.toString(), source, config, requestId, onEvent);
    }

    private String lastUser(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                return messages.get(i).content();
            }
        }
        return "";
    }

    private String ensureSession(ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            // 客户端恢复已有会话时沿用原 ID，历史读取仍由 Repository 的 session_id 条件隔离。
            return sessionId;
        }
        String id = UUID.randomUUID().toString();
        // 标题取首条用户消息，避免会话列表里全是无法区分的同名条目。
        chatRepository.insertSession(
                id, SessionService.titleFrom(request.messages()), System.currentTimeMillis());
        return id;
    }

    private void saveMessages(String sessionId, List<ChatMessage> messages, String assistant) {
        long now = System.currentTimeMillis();
        if (messages != null && !messages.isEmpty()) {
            // 桌面端每次只提交本轮输入；只保存最后一条可避免客户端重传历史导致数据库重复。
            ChatMessage last = messages.getLast();
            chatRepository.insertMessage(sessionId, last, now, tokenCounter.count(last.content()));
            promoteTitle(sessionId, messages, now);
        }
        chatRepository.insertMessage(
                sessionId,
                new ChatMessage("assistant", assistant),
                now,
                tokenCounter.count(assistant));
    }

    /** 空标题或旧占位名在首条用户消息到达时改成可读标题，方便侧栏区分。 */
    private void promoteTitle(String sessionId, List<ChatMessage> messages, long now) {
        chatRepository
                .findSession(sessionId)
                .ifPresent(
                        session -> {
                            String current = session.getTitle();
                            if (current != null
                                    && !current.isBlank()
                                    && !"session".equals(current)) {
                                return;
                            }
                            String title = SessionService.titleFrom(messages);
                            if (!title.isBlank()) {
                                chatRepository.updateTitle(sessionId, title, now);
                            }
                        });
    }
}
