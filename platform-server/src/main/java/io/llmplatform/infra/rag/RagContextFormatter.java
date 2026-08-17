package io.llmplatform.infra.rag;

import io.llmplatform.pojo.vo.RagChunk;
import java.util.ArrayList;
import java.util.List;

/** 把命中块格式化为带引用与字符预算的提示片段。 */
public final class RagContextFormatter {

    static final int DEFAULT_BUDGET = 3500;
    static final double MIN_SCORE = 0.008;

    private RagContextFormatter() {}

    public static String format(List<RagChunk> chunks) {
        return format(chunks, DEFAULT_BUDGET);
    }

    public static String format(List<RagChunk> chunks, int budget) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (RagChunk chunk : chunks) {
            if (chunk.score() < MIN_SCORE && index > 1) {
                continue;
            }
            String title = chunk.title() == null || chunk.title().isBlank() ? "" : chunk.title();
            String path = chunk.headingPath() == null ? "" : chunk.headingPath();
            String heading = path.isBlank() ? title : path;
            String block = "[" + index + "] " + heading + "\n" + chunk.content().trim();
            if (builder.length() + block.length() + 2 > budget) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(block);
            index++;
        }
        return builder.toString();
    }

    public static List<String> titles(List<RagChunk> chunks) {
        List<String> titles = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            if (chunk.title() != null && !chunk.title().isBlank()) {
                titles.add(chunk.title());
            }
        }
        return titles;
    }
}
