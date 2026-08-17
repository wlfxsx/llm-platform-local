package io.llmplatform.infra.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.repository.entity.RagChunkEntity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Cross-encoder 精排；进程未就绪时原样返回 RRF 顺序。 */
@Component
public class CrossEncoderReranker {

    private static final Logger log = LoggerFactory.getLogger(CrossEncoderReranker.class);
    private final RerankLlamafileManager manager;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CrossEncoderReranker(RerankLlamafileManager manager, ObjectMapper objectMapper) {
        this.manager = manager;
        this.objectMapper = objectMapper;
    }

    public List<String> rerank(String query, List<String> ids, Map<String, RagChunkEntity> chunks) {
        if (ids.isEmpty() || !manager.ensureReady()) {
            return ids;
        }
        List<String> documents = new ArrayList<>();
        for (String id : ids) {
            RagChunkEntity entity = chunks.get(id);
            documents.add(entity == null ? "" : entity.getContent());
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", BuiltinRagModels.RERANK_FILE);
            body.put("query", query);
            body.put("documents", documents);
            JsonNode results = post(body);
            if (results == null || !results.isArray()) {
                return ids;
            }
            List<Scored> scored = new ArrayList<>();
            for (JsonNode node : results) {
                int index = node.path("index").asInt(-1);
                double score =
                        node.path("relevance_score").asDouble(node.path("score").asDouble(0));
                if (index >= 0 && index < ids.size()) {
                    scored.add(new Scored(ids.get(index), score));
                }
            }
            if (scored.isEmpty()) {
                return ids;
            }
            scored.sort(Comparator.comparingDouble(Scored::score).reversed());
            return scored.stream().map(Scored::id).toList();
        } catch (Exception ex) {
            log.info("rerank 调用失败，回退 RRF 顺序");
            return ids;
        }
    }

    private JsonNode post(Map<String, Object> body) throws Exception {
        String payload = objectMapper.writeValueAsString(body);
        for (String path : List.of("/v1/rerank", "/rerank")) {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(manager.endpoint() + path))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                continue;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("results")) {
                return root.get("results");
            }
            if (root.isArray()) {
                return root;
            }
        }
        return null;
    }

    private record Scored(String id, double score) {}
}
