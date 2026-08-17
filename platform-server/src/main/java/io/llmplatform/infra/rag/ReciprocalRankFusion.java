package io.llmplatform.infra.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reciprocal Rank Fusion：多路召回按排名调和，不依赖不可比的原始分数。 */
public final class ReciprocalRankFusion {

    public static final int DEFAULT_K = 60;

    private ReciprocalRankFusion() {}

    public static List<String> fuse(List<List<String>> rankedLists, int k, int limit) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (List<String> ranked : rankedLists) {
            for (int i = 0; i < ranked.size(); i++) {
                String id = ranked.get(i);
                if (id == null || id.isBlank()) {
                    continue;
                }
                scores.merge(id, 1.0 / (k + i + 1), Double::sum);
            }
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(scores.entrySet());
        entries.sort(
                Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue)
                        .reversed());
        return entries.stream().limit(limit).map(Map.Entry::getKey).toList();
    }

    public static double scoreOf(List<List<String>> rankedLists, String id, int k) {
        double score = 0;
        for (List<String> ranked : rankedLists) {
            int index = ranked.indexOf(id);
            if (index >= 0) {
                score += 1.0 / (k + index + 1);
            }
        }
        return score;
    }
}
