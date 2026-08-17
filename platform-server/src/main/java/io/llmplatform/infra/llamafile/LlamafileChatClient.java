package io.llmplatform.infra.llamafile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.infra.llm.ChatModelClient;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.pojo.vo.ModelConfigView;
import io.llmplatform.service.ModelConfigService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** 直接调用 llamafile 的 OpenAI 兼容接口。 */
@Component
public class LlamafileChatClient implements ChatModelClient {

    private final LlamafileManager llamafileManager;
    private final ModelConfigService modelConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public LlamafileChatClient(
            LlamafileManager llamafileManager,
            ModelConfigService modelConfigService,
            ObjectMapper objectMapper) {
        this.llamafileManager = llamafileManager;
        this.modelConfigService = modelConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        return completeNonStream(messages, modelConfigService.current(), null, null);
    }

    /** 非流式调用允许摘要任务临时覆盖温度和输出长度，但仍沿用当前模型的其它采样参数。 */
    @Override
    public String completeNonStream(
            List<ChatMessage> messages,
            ModelConfigView config,
            Double temperatureOverride,
            Integer maxTokensOverride) {
        if (!llamafileManager.status().healthy()) {
            throw new PlatformException("MODEL_NOT_READY", "error.modelNotReady");
        }
        try {
            Map<String, Object> body =
                    buildBody(messages, config, false, temperatureOverride, maxTokensOverride);
            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            llamafileManager.endpoint() + "/v1/chat/completions"))
                            .timeout(Duration.ofMinutes(10))
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(body)))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new PlatformException("INTERNAL", "error.internal");
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isMissingNode() && !content.isNull()) {
                return content.asText();
            }
            // 兼容部分旧 llamafile 构建返回的 completion 风格 choices[0].text。
            return root.path("choices").path(0).path("text").asText("");
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException("INTERNAL", "error.internal");
        }
    }

    @Override
    public void stream(List<ChatMessage> messages, Consumer<String> onDelta) {
        stream(messages, modelConfigService.current(), onDelta);
    }

    @Override
    public void stream(
            List<ChatMessage> messages, ModelConfigView config, Consumer<String> onDelta) {
        if (!llamafileManager.status().healthy()) {
            throw new PlatformException("MODEL_NOT_READY", "error.modelNotReady");
        }
        try {
            Map<String, Object> body = buildBody(messages, config, true, null, null);
            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            llamafileManager.endpoint() + "/v1/chat/completions"))
                            .timeout(Duration.ofMinutes(10))
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(body)))
                            .build();
            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // llamafile 使用 OpenAI 风格 SSE；忽略心跳和非 data 行，只转发文本增量。
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
            throw new PlatformException("INTERNAL", "error.internal");
        }
    }

    private Map<String, Object> buildBody(
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
        body.put(
                "temperature",
                temperatureOverride != null ? temperatureOverride : config.temperature());
        body.put("top_p", config.topP());
        body.put("top_k", config.topK());
        body.put("min_p", config.minP());
        body.put("max_tokens", maxTokensOverride != null ? maxTokensOverride : config.maxTokens());
        body.put("repeat_penalty", config.repeatPenalty());
        body.put("repeat_last_n", config.repeatLastN());
        body.put("frequency_penalty", config.frequencyPenalty());
        body.put("presence_penalty", config.presencePenalty());
        if (config.seed() != null) {
            body.put("seed", config.seed());
        }
        if (config.stop() != null && !config.stop().isEmpty()) {
            body.put("stop", config.stop());
        }
        // 先写强类型参数，再合并扩展字段；保留协议字段最后写入，确保不能被扩展 JSON 覆盖。
        mergeAdvanced(body, config.advancedInferenceParams());
        body.put("model", "local");
        body.put("messages", payloadMessages);
        body.put("stream", stream);
        return body;
    }

    private void mergeAdvanced(Map<String, Object> body, JsonNode advanced) {
        if (advanced == null || !advanced.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = advanced.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            // 服务层已拒绝保留键，这里再次防御，避免绕过服务层的内部调用破坏请求结构。
            if ("model".equals(name) || "messages".equals(name) || "stream".equals(name)) {
                continue;
            }
            body.put(name, objectMapper.convertValue(field.getValue(), Object.class));
        }
    }
}
