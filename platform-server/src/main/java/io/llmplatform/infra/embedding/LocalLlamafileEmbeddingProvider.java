package io.llmplatform.infra.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.infra.rag.BuiltinRagModels;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 默认本地 embedding：调用独立 BGE-M3 llamafile 的 OpenAI 兼容接口。 */
@Component
public class LocalLlamafileEmbeddingProvider implements EmbeddingProvider {

    private static final String QUERY_PREFIX_ZH = "为这个句子生成表示以用于检索：";
    private static final String QUERY_PREFIX_EN =
            "Represent this sentence for searching relevant passages: ";

    private final EmbeddingLlamafileManager manager;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public LocalLlamafileEmbeddingProvider(
            EmbeddingLlamafileManager manager, ObjectMapper objectMapper) {
        this.manager = manager;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "local";
    }

    @Override
    public float[] embed(String text) {
        return embedRaw(text == null ? "" : text);
    }

    @Override
    public float[] embedQuery(String text) {
        String value = text == null ? "" : text;
        String prefix = containsHan(value) ? QUERY_PREFIX_ZH : QUERY_PREFIX_EN;
        return embedRaw(prefix + value);
    }

    @Override
    public float[] embedDocument(String text) {
        return embedRaw(text == null ? "" : text);
    }

    private float[] embedRaw(String text) {
        manager.ensureReady();
        try {
            String body =
                    objectMapper.writeValueAsString(
                            Map.of("model", BuiltinRagModels.EMBEDDING_FILE, "input", text));
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(manager.endpoint() + "/v1/embeddings"))
                            .timeout(Duration.ofSeconds(60))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new PlatformException("INTERNAL", "error.internal");
            }
            JsonNode values =
                    objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
            List<Float> list = new ArrayList<>();
            values.forEach(node -> list.add((float) node.asDouble()));
            if (list.isEmpty()) {
                throw new PlatformException("INTERNAL", "error.internal");
            }
            float[] vector = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                vector[i] = list.get(i);
            }
            return vector;
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException("INTERNAL", "error.internal");
        }
    }

    static boolean containsHan(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
