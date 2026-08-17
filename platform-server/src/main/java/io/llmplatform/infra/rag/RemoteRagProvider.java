package io.llmplatform.infra.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.constant.CapabilityIds;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.pojo.vo.RagChunk;
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

/** 远程 RAG HTTP 提供者，仅在联网与 RAG 同时开启时可用。 */
@Component
public class RemoteRagProvider implements RagProvider {

    private final CapabilityService capabilities;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RemoteRagProvider(
            CapabilityService capabilities,
            SettingsService settingsService,
            ObjectMapper objectMapper) {
        this.capabilities = capabilities;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "remote";
    }

    @Override
    public List<RagChunk> retrieve(String query, int topK) {
        capabilities.require(CapabilityIds.NETWORK);
        capabilities.require(CapabilityIds.RAG);
        AppSettings settings = settingsService.current();
        if (settings.ragRemoteUrl() == null || settings.ragRemoteUrl().isBlank()) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of("query", query, "topK", topK));
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(settings.ragRemoteUrl()))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode chunks = objectMapper.readTree(response.body()).path("chunks");
            List<RagChunk> result = new ArrayList<>();
            chunks.forEach(
                    node ->
                            result.add(
                                    new RagChunk(
                                            node.path("id").asText(),
                                            node.path("documentId").asText(),
                                            node.path("content").asText(),
                                            node.path("score").asDouble())));
            return result;
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException("INTERNAL", "error.internal");
        }
    }
}
