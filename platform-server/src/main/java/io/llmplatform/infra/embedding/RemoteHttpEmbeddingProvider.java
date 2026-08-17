package io.llmplatform.infra.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.constant.CapabilityIds;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.service.CapabilityService;
import io.llmplatform.service.SettingsService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 远程 HTTP Embedding，仅在联网与 embedding 能力同时开启时可用。 */
@Component
public class RemoteHttpEmbeddingProvider implements EmbeddingProvider {

    private final SettingsService settingsService;
    private final CapabilityService capabilities;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RemoteHttpEmbeddingProvider(
            SettingsService settingsService,
            CapabilityService capabilities,
            ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.capabilities = capabilities;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "remote";
    }

    @Override
    public float[] embed(String text) {
        capabilities.require(CapabilityIds.NETWORK);
        capabilities.require(CapabilityIds.EMBEDDING);
        AppSettings settings = settingsService.current();
        if (settings.embeddingBaseUrl() == null || settings.embeddingBaseUrl().isBlank()) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
        String modelName =
                settings.embeddingModel() == null || settings.embeddingModel().isBlank()
                        ? "embedding"
                        : settings.embeddingModel();
        try {
            String body =
                    objectMapper.writeValueAsString(Map.of("model", modelName, "input", text));
            String url = settings.embeddingBaseUrl().replaceAll("/$", "") + "/v1/embeddings";
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.path("data").path(0).path("embedding");
            List<Float> list = new ArrayList<>();
            values.forEach(node -> list.add((float) node.asDouble()));
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
}
