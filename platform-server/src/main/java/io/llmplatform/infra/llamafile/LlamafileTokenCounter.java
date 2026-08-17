package io.llmplatform.infra.llamafile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 优先调用 llamafile /tokenize；失败时使用明确标记的保守估算器。 */
@Primary
@Component
public class LlamafileTokenCounter implements TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(LlamafileTokenCounter.class);
    private final LlamafileManager llamafileManager;
    private final ConservativeTokenCounter conservative;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public LlamafileTokenCounter(
            LlamafileManager llamafileManager,
            ConservativeTokenCounter conservative,
            ObjectMapper objectMapper) {
        this.llamafileManager = llamafileManager;
        this.conservative = conservative;
        this.objectMapper = objectMapper;
    }

    @Override
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (!llamafileManager.status().healthy()) {
            return conservative.count(text);
        }
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(llamafileManager.endpoint() + "/tokenize"))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(
                                                    Map.of("content", text))))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return conservative.count(text);
            }
            JsonNode tokens = objectMapper.readTree(response.body()).path("tokens");
            if (tokens.isArray() && tokens.size() > 0) {
                return tokens.size();
            }
            return conservative.count(text);
        } catch (Exception ex) {
            log.debug("tokenize 不可用，改用保守估算");
            return conservative.count(text);
        }
    }

    @Override
    public boolean accurate() {
        return llamafileManager.status().healthy();
    }
}
