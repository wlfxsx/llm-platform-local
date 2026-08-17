package io.llmplatform.infra.rag;

import io.llmplatform.infra.embedding.EmbeddingProvider;
import io.llmplatform.pojo.vo.RagChunk;
import io.llmplatform.pojo.vo.VectorHit;
import io.llmplatform.repository.RagRepository;
import io.llmplatform.repository.VectorStore;
import io.llmplatform.repository.entity.RagChunkEntity;
import io.llmplatform.repository.entity.RagDocumentEntity;
import io.llmplatform.service.EmbeddingService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 向量 + FTS5 → RRF → rerank → 父子扩展；HyDE/Graph 仅远程门禁通过时并入。 */
@Component
public class HybridRagRetriever {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final RagRepository ragRepository;
    private final CrossEncoderReranker reranker;
    private final HydeQueryExpander hydeQueryExpander;
    private final GraphRagRetriever graphRagRetriever;

    public HybridRagRetriever(
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            RagRepository ragRepository,
            CrossEncoderReranker reranker,
            HydeQueryExpander hydeQueryExpander,
            GraphRagRetriever graphRagRetriever) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.ragRepository = ragRepository;
        this.reranker = reranker;
        this.hydeQueryExpander = hydeQueryExpander;
        this.graphRagRetriever = graphRagRetriever;
    }

    public List<RagChunk> retrieve(String query, String sessionId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        EmbeddingProvider embedding = embeddingService.current();
        List<String> denseIds = ids(vectorStore.search("rag", embedding.embedQuery(query), 20));
        String hyde = hydeQueryExpander.expand(query, sessionId);
        List<String> hydeIds =
                hyde.isBlank()
                        ? List.of()
                        : ids(vectorStore.search("rag", embedding.embedDocument(hyde), 20));
        List<String> ftsIds = ragRepository.searchFts(FtsQuerySanitizer.sanitize(query), 20);
        List<List<String>> lists = new ArrayList<>();
        lists.add(denseIds);
        if (!hydeIds.isEmpty()) {
            lists.add(hydeIds);
        }
        lists.add(ftsIds);
        List<String> fused = ReciprocalRankFusion.fuse(lists, ReciprocalRankFusion.DEFAULT_K, 20);
        Map<String, RagChunkEntity> loaded = load(fused);
        List<String> reranked = reranker.rerank(query, fused, loaded);
        List<RagChunk> chunks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : reranked) {
            RagChunkEntity child = loaded.get(id);
            if (child == null) {
                child = ragRepository.findChunk(id).orElse(null);
            }
            if (child == null) {
                continue;
            }
            RagChunkEntity inject = expandParent(child);
            String key = inject.getId();
            if (!seen.add(key)) {
                continue;
            }
            double score = ReciprocalRankFusion.scoreOf(lists, id, ReciprocalRankFusion.DEFAULT_K);
            String title =
                    ragRepository
                            .findDocument(inject.getDocumentId())
                            .map(RagDocumentEntity::getTitle)
                            .orElse("");
            chunks.add(
                    new RagChunk(
                            inject.getId(),
                            inject.getDocumentId(),
                            inject.getContent(),
                            score,
                            title,
                            inject.getHeadingPath()));
            if (chunks.size() >= topK) {
                break;
            }
        }
        for (RetrievedChunk extra : graphRagRetriever.retrieve(query, sessionId, 4)) {
            if (!seen.add(extra.id())) {
                continue;
            }
            if (chunks.size() >= topK + 2) {
                break;
            }
            chunks.add(
                    new RagChunk(
                            extra.id(),
                            extra.documentId(),
                            extra.content(),
                            extra.score(),
                            extra.title(),
                            extra.headingPath()));
        }
        return chunks;
    }

    private RagChunkEntity expandParent(RagChunkEntity child) {
        if (child.getParentId() == null || child.getParentId().isBlank()) {
            return child;
        }
        return ragRepository.findChunk(child.getParentId()).orElse(child);
    }

    private Map<String, RagChunkEntity> load(List<String> ids) {
        Map<String, RagChunkEntity> map = new LinkedHashMap<>();
        for (String id : ids) {
            ragRepository.findChunk(id).ifPresent(entity -> map.put(id, entity));
        }
        return map;
    }

    private static List<String> ids(List<VectorHit> hits) {
        return hits.stream().map(VectorHit::id).toList();
    }
}
