package io.llmplatform.controller;

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

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @GetMapping
    public Map<String, Object> current() {
        return Map.of("provider", embeddingService.current().id());
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody Map<String, String> body) {
        float[] vector = embeddingService.current().embed(body.getOrDefault("text", "test"));
        return Map.of("provider", embeddingService.current().id(), "dimension", vector.length);
    }
}
