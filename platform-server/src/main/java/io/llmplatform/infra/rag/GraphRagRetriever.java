package io.llmplatform.infra.rag;

import io.llmplatform.repository.GraphRagRepository;
import io.llmplatform.repository.GraphRagRepository.EdgeRow;
import io.llmplatform.repository.GraphRagRepository.EntityRow;
import io.llmplatform.repository.RagRepository;
import io.llmplatform.repository.entity.RagChunkEntity;
import io.llmplatform.repository.entity.RagDocumentEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 1～2 跳扩展：从问句命中的实体走到邻居证据块。 */
@Component
public class GraphRagRetriever {

    private final RemoteEnhancementGate gate;
    private final GraphRagRepository graphRagRepository;
    private final RagRepository ragRepository;

    public GraphRagRetriever(
            RemoteEnhancementGate gate,
            GraphRagRepository graphRagRepository,
            RagRepository ragRepository) {
        this.gate = gate;
        this.graphRagRepository = graphRagRepository;
        this.ragRepository = ragRepository;
    }

    public List<RetrievedChunk> retrieve(String query, String sessionId, int limit) {
        if (query == null || query.isBlank() || !gate.graphAllowed(sessionId)) {
            return List.of();
        }
        List<EntityRow> seeds = graphRagRepository.findEntitiesMentioned(query);
        if (seeds.isEmpty()) {
            return List.of();
        }
        List<String> seedIds = seeds.stream().map(EntityRow::id).toList();
        List<EdgeRow> first = graphRagRepository.findEdgesTouching(seedIds);
        Set<String> hopIds = new LinkedHashSet<>(seedIds);
        for (EdgeRow edge : first) {
            hopIds.add(edge.fromId());
            hopIds.add(edge.toId());
        }
        List<EdgeRow> second = graphRagRepository.findEdgesTouching(new ArrayList<>(hopIds));
        for (EdgeRow edge : second) {
            hopIds.add(edge.fromId());
            hopIds.add(edge.toId());
        }
        Map<String, String> names = namesOf(hopIds);
        Set<String> evidence = new LinkedHashSet<>();
        List<String> relationLines = new ArrayList<>();
        for (EdgeRow edge : second) {
            if (edge.evidenceChunkId() != null && !edge.evidenceChunkId().isBlank()) {
                evidence.add(edge.evidenceChunkId());
            }
            relationLines.add(formatRelation(edge, names));
        }
        List<RetrievedChunk> chunks = new ArrayList<>();
        int ordinal = 0;
        for (String id : evidence) {
            if (chunks.size() >= limit) {
                break;
            }
            RagChunkEntity entity = ragRepository.findChunk(id).orElse(null);
            if (entity == null) {
                continue;
            }
            String title =
                    ragRepository
                            .findDocument(entity.getDocumentId())
                            .map(RagDocumentEntity::getTitle)
                            .orElse("");
            String extra = ordinal < relationLines.size() ? relationLines.get(ordinal) + "\n" : "";
            chunks.add(
                    new RetrievedChunk(
                            entity.getId(),
                            entity.getDocumentId(),
                            extra + entity.getContent(),
                            0.02,
                            title,
                            entity.getHeadingPath()));
            ordinal++;
        }
        return chunks;
    }

    private Map<String, String> namesOf(Set<String> ids) {
        Map<String, String> names = new LinkedHashMap<>();
        for (EntityRow row : graphRagRepository.findEntitiesByIds(new ArrayList<>(ids))) {
            names.put(row.id(), row.name());
        }
        return names;
    }

    private static String formatRelation(EdgeRow edge, Map<String, String> names) {
        String from = names.getOrDefault(edge.fromId(), edge.fromId());
        String to = names.getOrDefault(edge.toId(), edge.toId());
        return from + " -" + edge.predicate() + "-> " + to;
    }
}
