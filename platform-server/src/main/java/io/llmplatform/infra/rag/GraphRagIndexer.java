package io.llmplatform.infra.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.infra.llm.OpenAiCompatibleChatClient;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.repository.GraphRagRepository;
import io.llmplatform.repository.RagRepository;
import io.llmplatform.repository.entity.RagChunkEntity;
import io.llmplatform.service.ModelConfigService;
import io.llmplatform.websocket.PlatformWebSocketHandler;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 导入后用远程模型按父块抽实体关系；本地对话路径直接跳过。 */
@Component
public class GraphRagIndexer {

    private static final Logger log = LoggerFactory.getLogger(GraphRagIndexer.class);
    private final RemoteEnhancementGate gate;
    private final OpenAiCompatibleChatClient remoteChat;
    private final ModelConfigService modelConfigService;
    private final RagRepository ragRepository;
    private final GraphRagRepository graphRagRepository;
    private final ObjectMapper objectMapper;
    private final PlatformWebSocketHandler webSocketHandler;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "graph-rag-indexer");
                        thread.setDaemon(true);
                        return thread;
                    });

    public GraphRagIndexer(
            RemoteEnhancementGate gate,
            OpenAiCompatibleChatClient remoteChat,
            ModelConfigService modelConfigService,
            RagRepository ragRepository,
            GraphRagRepository graphRagRepository,
            ObjectMapper objectMapper,
            PlatformWebSocketHandler webSocketHandler) {
        this.gate = gate;
        this.remoteChat = remoteChat;
        this.modelConfigService = modelConfigService;
        this.ragRepository = ragRepository;
        this.graphRagRepository = graphRagRepository;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
    }

    public void schedule(String documentId, String sessionId) {
        if (!gate.graphAllowed(sessionId)) {
            return;
        }
        // 导入在事务内写块；提交前查询会读到空结果，因此抽图必须等到 afterCommit。
        Runnable task = () -> index(documentId, sessionId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            executor.submit(task);
                        }
                    });
            return;
        }
        executor.submit(task);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    void index(String documentId, String sessionId) {
        if (!gate.graphAllowed(sessionId)) {
            return;
        }
        try {
            graphRagRepository.deleteByDocument(documentId);
            List<RagChunkEntity> parents = ragRepository.findParents(documentId);
            if (parents.isEmpty()) {
                parents = ragRepository.findChildren(documentId);
            }
            int done = 0;
            for (RagChunkEntity parent : parents) {
                if (!gate.graphAllowed(sessionId)) {
                    return;
                }
                if (parent.getContent() == null || parent.getContent().length() < 40) {
                    continue;
                }
                extractInto(documentId, parent);
                done++;
                webSocketHandler.publish(
                        "rag.graph", documentId, Map.of("done", done, "total", parents.size()));
            }
        } catch (RuntimeException ex) {
            log.info("GraphRAG 抽图失败，块检索仍可用");
            webSocketHandler.publish("rag.graph", documentId, Map.of("error", "error.internal"));
        }
    }

    private void extractInto(String documentId, RagChunkEntity parent) {
        List<ChatMessage> messages =
                List.of(
                        new ChatMessage(
                                "system",
                                "Extract entities and relations as JSON only: {\"entities\":[{\"name\":\"\",\"type\":\"\"}],\"relations\":[{\"from\":\"\",\"to\":\"\",\"predicate\":\"\"}]}. No markdown."),
                        new ChatMessage("user", parent.getContent()));
        String raw =
                remoteChat.completeNonStream(
                        messages, modelConfigService.currentForInference(), 0.0, 512);
        JsonNode root = parseJson(raw);
        if (root == null) {
            return;
        }
        Map<String, String> ids = new LinkedHashMap<>();
        JsonNode entities = root.path("entities");
        if (entities.isArray()) {
            for (JsonNode node : entities) {
                String name = node.path("name").asText("").trim();
                if (name.isBlank()) {
                    continue;
                }
                String type = node.path("type").asText("concept");
                String normalized = name.toLowerCase(Locale.ROOT);
                String id = graphRagRepository.findId(documentId, normalized);
                if (id == null) {
                    id = UUID.randomUUID().toString();
                    graphRagRepository.insertEntity(id, name, normalized, type, documentId);
                }
                graphRagRepository.insertEntityChunk(id, parent.getId());
                ids.put(normalized, id);
            }
        }
        JsonNode relations = root.path("relations");
        if (relations.isArray()) {
            for (JsonNode node : relations) {
                String from = node.path("from").asText("").trim().toLowerCase(Locale.ROOT);
                String to = node.path("to").asText("").trim().toLowerCase(Locale.ROOT);
                String predicate = node.path("predicate").asText("related_to").trim();
                String fromId = resolveEntityId(ids, documentId, from);
                String toId = resolveEntityId(ids, documentId, to);
                if (fromId == null || toId == null) {
                    continue;
                }
                graphRagRepository.insertEdge(
                        UUID.randomUUID().toString(),
                        fromId,
                        toId,
                        predicate,
                        parent.getId(),
                        documentId);
            }
        }
    }

    private String resolveEntityId(Map<String, String> ids, String documentId, String normalized) {
        String id = ids.get(normalized);
        return id != null ? id : graphRagRepository.findId(documentId, normalized);
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(text.substring(start, end + 1));
        } catch (Exception ex) {
            return null;
        }
    }
}
