package io.llmplatform.infra.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.infra.llamafile.LlamafileManager;
import io.llmplatform.pojo.vo.InferenceMetrics;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 尽力解析 llamafile /metrics 与 /slots；失败时返回空指标而不是抛错。 */
@Component
public class LlamafileInferenceSampler {

    private final LlamafileManager llamafileManager;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    public LlamafileInferenceSampler(LlamafileManager llamafileManager, ObjectMapper objectMapper) {
        this.llamafileManager = llamafileManager;
        this.objectMapper = objectMapper;
    }

    public InferenceMetrics sample() {
        if (!llamafileManager.status().healthy()) {
            return new InferenceMetrics(null, null, null, null, null);
        }
        String body = get(llamafileManager.endpoint() + "/metrics");
        // llamafile 与 llama.cpp 各版本的指标名不同，按实际出现过的写法依次匹配。
        Double tokensPerSecond =
                parseMetric(
                        body,
                        "predicted_tokens_seconds",
                        "tokens_predicted_per_second",
                        "predicted_per_second");
        Double kv = parseMetric(body, "kv_cache_usage_ratio");
        Integer active = parseIntMetric(body, "requests_processing", "n_busy_slots");
        Integer contextUsed = parseIntMetric(body, "kv_cache_tokens", "n_tokens");
        Integer contextSize = parseIntMetric(body, "n_ctx", "ctx_size");
        InferenceMetrics fromSlots = sampleSlots();
        return new InferenceMetrics(
                active != null ? active : fromSlots.activeRequests(),
                tokensPerSecond != null ? tokensPerSecond : fromSlots.tokensPerSecond(),
                contextUsed != null ? contextUsed : fromSlots.contextUsed(),
                contextSize != null ? contextSize : fromSlots.contextSize(),
                kv != null ? kv * (kv <= 1 ? 100d : 1d) : fromSlots.kvCachePercent());
    }

    private InferenceMetrics sampleSlots() {
        String body = get(llamafileManager.endpoint() + "/slots");
        if (body == null || body.isBlank()) {
            return new InferenceMetrics(null, null, null, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode array = root.isArray() ? root : root.path("slots");
            if (!array.isArray() || array.isEmpty()) {
                return new InferenceMetrics(null, null, null, null, null);
            }
            int busy = 0;
            Integer ctx = null;
            Integer used = null;
            for (JsonNode slot : array) {
                if (slot.path("is_processing").asBoolean(false)
                        || "busy".equalsIgnoreCase(slot.path("state").asText())) {
                    busy++;
                }
                if (slot.has("n_ctx")) {
                    ctx = slot.path("n_ctx").asInt();
                }
                if (slot.has("n_decoded") || slot.has("n_prompt_tokens") || slot.has("n_past")) {
                    used = slot.path("n_past").asInt(slot.path("n_decoded").asInt(0));
                }
            }
            Double kv = ctx != null && ctx > 0 && used != null ? (used * 100d / ctx) : null;
            return new InferenceMetrics(busy, null, used, ctx, kv);
        } catch (Exception ex) {
            return new InferenceMetrics(null, null, null, null, null);
        }
    }

    private String get(String url) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(1))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) {
                return null;
            }
            return response.body();
        } catch (Exception ex) {
            return null;
        }
    }

    private static Double parseMetric(String body, String... keys) {
        String line = findLine(body, keys);
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Double.parseDouble(parts[parts.length - 1]);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer parseIntMetric(String body, String... keys) {
        Double value = parseMetric(body, keys);
        return value == null ? null : value.intValue();
    }

    private static String findLine(String body, String... keys) {
        if (body == null || body.isBlank()) {
            return null;
        }
        for (String raw : body.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            for (String key : keys) {
                if (lower.contains(key.toLowerCase(Locale.ROOT))) {
                    return line;
                }
            }
        }
        return null;
    }
}
