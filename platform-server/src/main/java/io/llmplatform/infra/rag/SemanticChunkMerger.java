package io.llmplatform.infra.rag;

import io.llmplatform.infra.embedding.EmbeddingProvider;
import java.util.ArrayList;
import java.util.List;

/** 相邻且合并后仍不超过上限、余弦相似度足够高时才合并，减少碎块。 */
public final class SemanticChunkMerger {

    static final double THRESHOLD = 0.82;

    private SemanticChunkMerger() {}

    public static List<ChunkDraft> merge(List<ChunkDraft> drafts, EmbeddingProvider embedding) {
        if (drafts.size() <= 1 || embedding == null) {
            return drafts;
        }
        List<ChunkDraft> current = new ArrayList<>(drafts);
        boolean changed = true;
        while (changed && current.size() > 1) {
            changed = false;
            List<ChunkDraft> next = new ArrayList<>();
            int i = 0;
            while (i < current.size()) {
                if (i == current.size() - 1) {
                    next.add(current.get(i));
                    break;
                }
                ChunkDraft left = current.get(i);
                ChunkDraft right = current.get(i + 1);
                if (canMerge(left, right, embedding)) {
                    next.add(join(left, right));
                    i += 2;
                    changed = true;
                } else {
                    next.add(left);
                    i += 1;
                }
            }
            current = next;
        }
        return current;
    }

    private static boolean canMerge(
            ChunkDraft left, ChunkDraft right, EmbeddingProvider embedding) {
        if (!safeEquals(left.headingPath(), right.headingPath())) {
            return false;
        }
        if (left.body().length() + right.body().length() + 2 > DocumentChunker.MAX_CHARS) {
            return false;
        }
        try {
            float[] a = embedding.embed(left.body());
            float[] b = embedding.embed(right.body());
            return cosine(a, b) >= THRESHOLD;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static ChunkDraft join(ChunkDraft left, ChunkDraft right) {
        return new ChunkDraft(
                left.headingPath(),
                left.body() + "\n\n" + right.body(),
                left.charStart(),
                right.charEnd());
    }

    static double cosine(float[] left, float[] right) {
        int size = Math.min(left.length, right.length);
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < size; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static boolean safeEquals(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.equals(right);
    }
}
