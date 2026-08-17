package io.llmplatform.controller;

import io.llmplatform.infra.embedding.EmbeddingLlamafileManager;
import io.llmplatform.service.EmbeddingService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Embedding 提供者状态与测试。 */
@RestController
@RequestMapping("/api/embedding")
public class EmbeddingController {

    private final EmbeddingService embeddingService;
    private final EmbeddingLlamafileManager embeddingLlamafileManager;

    public EmbeddingController(
            EmbeddingService embeddingService,
            EmbeddingLlamafileManager embeddingLlamafileManager) {
        this.embeddingService = embeddingService;
        this.embeddingLlamafileManager = embeddingLlamafileManager;
    }

    @GetMapping
    public Map<String, Object> current() {
        return Map.of(
                "provider",
                embeddingService.current().id(),
                "healthy",
                embeddingLlamafileManager.status().healthy(),
                "modelPresent",
                embeddingLlamafileManager.modelPresent());
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody Map<String, String> body) {
        float[] vector = embeddingService.current().embed(body.getOrDefault("text", "test"));
        return Map.of("provider", embeddingService.current().id(), "dimension", vector.length);
    }
}
