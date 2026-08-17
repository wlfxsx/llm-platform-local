package io.llmplatform.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.pojo.vo.ModelConfigView;
import io.llmplatform.service.ModelConfigService;
import io.llmplatform.service.RemoteModelService;
import io.llmplatform.service.RemoteModelService.ResolvedRemoteModel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** OpenAI 兼容远程聊天客户端；只发送可映射的采样字段。 */
@Component
public class OpenAiCompatibleChatClient implements ChatModelClient {

    private final RemoteModelService remoteModelService;
    private final ModelConfigService modelConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public OpenAiCompatibleChatClient(
            RemoteModelService remoteModelService,
            ModelConfigService modelConfigService,
            ObjectMapper objectMapper) {
        this.remoteModelService = remoteModelService;
        this.modelConfigService = modelConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        return completeNonStream(messages, modelConfigService.currentForInference(), null, null);
    }

    @Override
    public String completeNonStream(
            List<ChatMessage> messages,
            ModelConfigView config,
            Double temperatureOverride,
            Integer maxTokensOverride) {
        ResolvedRemoteModel remote = remoteModelService.resolveCurrent();
        try {
            Map<String, Object> body =
                    buildBody(
                            remote.modelName(),
                            messages,
                            config,
                            false,
                            temperatureOverride,
                            maxTokensOverride);
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(remote.baseUrl() + "/chat/completions"))
                            .timeout(Duration.ofMinutes(10))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + remote.apiKey())
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(body)))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new PlatformException("REMOTE_UNAUTHORIZED", "error.remoteUnauthorized");
            }
            if (response.statusCode() >= 400) {
                throw new PlatformException("REMOTE_UNREACHABLE", "error.remoteUnreachable");
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException("REMOTE_UNREACHABLE", "error.remoteUnreachable");
        }
    }

    @Override
    public void stream(List<ChatMessage> messages, Consumer<String> onDelta) {
        stream(messages, modelConfigService.currentForInference(), onDelta);
    }

    @Override
    public void stream(
            List<ChatMessage> messages, ModelConfigView config, Consumer<String> onDelta) {
        ResolvedRemoteModel remote = remoteModelService.resolveCurrent();
        try {
            Map<String, Object> body =
                    buildBody(remote.modelName(), messages, config, true, null, null);
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(remote.baseUrl() + "/chat/completions"))
                            .timeout(Duration.ofMinutes(10))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + remote.apiKey())
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(body)))
                            .build();
            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new PlatformException("REMOTE_UNAUTHORIZED", "error.remoteUnauthorized");
            }
            if (response.statusCode() >= 400) {
                throw new PlatformException("REMOTE_UNREACHABLE", "error.remoteUnreachable");
            }
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull()) {
                        onDelta.accept(delta.asText());
                    }
                }
            }
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException("REMOTE_UNREACHABLE", "error.remoteUnreachable");
        }
    }

    private Map<String, Object> buildBody(
            String model,
            List<ChatMessage> messages,
            ModelConfigView config,
            boolean stream,
            Double temperatureOverride,
            Integer maxTokensOverride) {
        List<Map<String, String>> payloadMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            payloadMessages.add(Map.of("role", message.role(), "content", message.content()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", payloadMessages);
        body.put("stream", stream);
        body.put(
                "temperature",
                temperatureOverride != null ? temperatureOverride : config.temperature());
        body.put("top_p", config.topP());
        body.put("max_tokens", maxTokensOverride != null ? maxTokensOverride : config.maxTokens());
        body.put("frequency_penalty", config.frequencyPenalty());
        body.put("presence_penalty", config.presencePenalty());
        if (config.seed() != null) {
            body.put("seed", config.seed());
        }
        if (config.stop() != null && !config.stop().isEmpty()) {
            body.put("stop", config.stop());
        }
        return body;
    }
}
