package io.llmplatform.controller;

import io.llmplatform.infra.rag.LocalRagProvider;
import io.llmplatform.pojo.dto.PathImportRequest;
import io.llmplatform.pojo.dto.RagSearchRequest;
import io.llmplatform.pojo.entity.RagDocument;
import io.llmplatform.pojo.vo.RagChunk;
import io.llmplatform.service.RagService;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本地知识库文档与检索。 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final LocalRagProvider localRagProvider;

    public RagController(RagService ragService, LocalRagProvider localRagProvider) {
        this.ragService = ragService;
        this.localRagProvider = localRagProvider;
    }

    @GetMapping("/documents")
    public List<RagDocument> documents() {
        return localRagProvider.listDocuments();
    }

    @PostMapping("/documents/import")
    public RagDocument importDocument(@Valid @RequestBody PathImportRequest body) {
        return localRagProvider.importDocument(Path.of(body.path()));
    }

    @DeleteMapping("/documents/{id}")
    public void delete(@PathVariable String id) {
        localRagProvider.deleteDocument(id);
    }

    @PostMapping("/search")
    public List<RagChunk> search(@Valid @RequestBody RagSearchRequest body) {
        String sessionId = body.sessionId() == null ? "" : body.sessionId();
        String text = ragService.retrieve(body.query(), sessionId);
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(new RagChunk("preview", "", text, 1.0));
    }
}
