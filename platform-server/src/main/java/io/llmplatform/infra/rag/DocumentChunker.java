package io.llmplatform.infra.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 结构感知递归切分：标题/段落优先，带 overlap，超长无标点走 hardMax。 */
public final class DocumentChunker {

    static final int MAX_CHARS = 800;
    static final int OVERLAP_CHARS = 120;
    static final int HARD_MAX_CHARS = 1600;
    static final int PARENT_MAX_CHARS = 2200;

    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");
    private static final Pattern FENCE = Pattern.compile("(?s)```.*?```");
    private static final String[] MARKDOWN_SEPARATORS = {
        "\n## ",
        "\n### ",
        "\n#### ",
        "\n##### ",
        "\n\n",
        "\n",
        "。",
        "！",
        "？",
        "；",
        ".",
        "!",
        "?",
        ";",
        " "
    };
    private static final String[] CODE_SEPARATORS = {"\n\n", "\n", ";", "{", "}", " "};
    private static final String[] PLAIN_SEPARATORS = {
        "\n\n", "\n", "。", "！", "？", "；", ".", "!", "?", ";", " "
    };

    private DocumentChunker() {}

    public static List<ChunkDraft> chunk(String text, DocumentKind kind, String title) {
        String source = text == null ? "" : text;
        if (source.isBlank()) {
            return List.of(new ChunkDraft(title, source, 0, 0));
        }
        List<Section> sections =
                kind == DocumentKind.MARKDOWN
                        ? splitMarkdown(source, title)
                        : List.of(new Section(title, source, 0));
        String[] separators =
                switch (kind) {
                    case MARKDOWN -> MARKDOWN_SEPARATORS;
                    case CODE -> CODE_SEPARATORS;
                    case PLAIN -> PLAIN_SEPARATORS;
                };
        List<ChunkDraft> drafts = new ArrayList<>();
        for (Section section : sections) {
            List<String> pieces = recursiveSplit(section.body(), separators);
            List<String> windowed = applyOverlap(pieces);
            int cursor = section.start();
            for (String piece : windowed) {
                String body = piece.trim();
                if (body.isEmpty()) {
                    continue;
                }
                int start = indexOfFrom(source, body, Math.max(0, cursor - OVERLAP_CHARS));
                if (start < 0) {
                    start = cursor;
                }
                int end = Math.min(source.length(), start + body.length());
                drafts.add(new ChunkDraft(section.headingPath(), body, start, end));
                cursor = end;
            }
        }
        if (drafts.isEmpty()) {
            drafts.add(new ChunkDraft(title, source.trim(), 0, source.length()));
        }
        return drafts;
    }

    /** 同一标题路径下相邻子块合成父段，供命中后注入。 */
    public static List<ParentGroup> groupParents(List<ChunkDraft> children) {
        List<ParentGroup> groups = new ArrayList<>();
        List<ChunkDraft> bucket = new ArrayList<>();
        String path = null;
        int chars = 0;
        for (ChunkDraft child : children) {
            boolean newPath = path != null && !path.equals(child.headingPath());
            int extra = child.body().length() + 2;
            if (!bucket.isEmpty() && (newPath || chars + extra > PARENT_MAX_CHARS)) {
                groups.add(new ParentGroup(path, List.copyOf(bucket)));
                bucket.clear();
                chars = 0;
            }
            path = child.headingPath();
            bucket.add(child);
            chars += extra;
        }
        if (!bucket.isEmpty()) {
            groups.add(new ParentGroup(path, List.copyOf(bucket)));
        }
        return groups;
    }

    private static List<Section> splitMarkdown(String text, String title) {
        List<Section> sections = new ArrayList<>();
        Matcher fences = FENCE.matcher(text);
        List<int[]> atomic = new ArrayList<>();
        while (fences.find()) {
            atomic.add(new int[] {fences.start(), fences.end()});
        }
        Matcher headings = HEADING.matcher(text);
        List<int[]> headingSpans = new ArrayList<>();
        List<String> headingTitles = new ArrayList<>();
        while (headings.find()) {
            if (covered(atomic, headings.start())) {
                continue;
            }
            headingSpans.add(new int[] {headings.start(), headings.end()});
            headingTitles.add(title + " > " + headings.group(2).trim());
        }
        if (headingSpans.isEmpty()) {
            addAtomicSections(sections, text, title, 0, text.length(), atomic);
            return sections;
        }
        if (headingSpans.getFirst()[0] > 0) {
            addAtomicSections(sections, text, title, 0, headingSpans.getFirst()[0], atomic);
        }
        for (int i = 0; i < headingSpans.size(); i++) {
            int start = headingSpans.get(i)[1];
            int end = i + 1 < headingSpans.size() ? headingSpans.get(i + 1)[0] : text.length();
            addAtomicSections(sections, text, headingTitles.get(i), start, end, atomic);
        }
        return sections;
    }

    private static void addAtomicSections(
            List<Section> sections,
            String text,
            String path,
            int from,
            int to,
            List<int[]> atomic) {
        int cursor = from;
        for (int[] span : atomic) {
            if (span[1] <= from || span[0] >= to) {
                continue;
            }
            int start = Math.max(span[0], from);
            int end = Math.min(span[1], to);
            if (cursor < start) {
                String prefix = text.substring(cursor, start).trim();
                if (!prefix.isEmpty()) {
                    sections.add(new Section(path, prefix, cursor));
                }
            }
            sections.add(new Section(path, text.substring(start, end).trim(), start));
            cursor = end;
        }
        if (cursor < to) {
            String rest = text.substring(cursor, to).trim();
            if (!rest.isEmpty()) {
                sections.add(new Section(path, rest, cursor));
            }
        }
    }

    private static boolean covered(List<int[]> atomic, int index) {
        for (int[] span : atomic) {
            if (index >= span[0] && index < span[1]) {
                return true;
            }
        }
        return false;
    }

    static List<String> recursiveSplit(String text, String[] separators) {
        String value = text.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        if (value.length() <= MAX_CHARS) {
            return List.of(value);
        }
        for (String separator : separators) {
            List<String> parts = splitKeep(value, separator);
            if (parts.size() <= 1) {
                continue;
            }
            List<String> merged = mergeParts(parts);
            List<String> result = new ArrayList<>();
            for (String part : merged) {
                if (part.length() <= HARD_MAX_CHARS) {
                    if (part.length() <= MAX_CHARS) {
                        result.add(part);
                    } else {
                        result.addAll(hardSplit(part));
                    }
                } else {
                    result.addAll(recursiveSplit(part, separators));
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return hardSplit(value);
    }

    private static List<String> mergeParts(List<String> parts) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (current.isEmpty()) {
                current.append(part);
                continue;
            }
            if (current.length() + 1 + part.length() <= MAX_CHARS) {
                current.append('\n').append(part);
            } else {
                merged.add(current.toString().trim());
                current.setLength(0);
                current.append(part);
            }
        }
        if (!current.isEmpty()) {
            merged.add(current.toString().trim());
        }
        return merged;
    }

    private static List<String> splitKeep(String text, String separator) {
        if (separator.isEmpty()) {
            return List.of(text);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        int index;
        while ((index = text.indexOf(separator, start + (start == 0 ? 1 : 0))) >= 0) {
            if (index <= start) {
                start = index + separator.length();
                continue;
            }
            parts.add(text.substring(start, index).trim());
            start = index + (separator.startsWith("\n") ? 1 : separator.length());
        }
        if (start < text.length()) {
            parts.add(text.substring(start).trim());
        }
        parts.removeIf(String::isBlank);
        return parts;
    }

    static List<String> hardSplit(String text) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + HARD_MAX_CHARS);
            if (end < text.length()) {
                int breakAt = lastBreak(text, i, end);
                if (breakAt > i + MAX_CHARS / 2) {
                    end = breakAt;
                }
            }
            String piece = text.substring(i, end).trim();
            if (!piece.isEmpty()) {
                parts.add(piece);
            }
            i = end;
        }
        return parts;
    }

    private static int lastBreak(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == ' ' || c == '。' || c == '.' || c == '！' || c == '？') {
                return i + 1;
            }
        }
        return end;
    }

    static List<String> applyOverlap(List<String> pieces) {
        if (pieces.size() <= 1) {
            return pieces;
        }
        List<String> result = new ArrayList<>();
        String previous = "";
        for (String piece : pieces) {
            if (previous.isEmpty()) {
                result.add(piece);
                previous = piece;
                continue;
            }
            String overlap = overlapSuffix(previous);
            if (!overlap.isEmpty() && !piece.startsWith(overlap)) {
                result.add((overlap + "\n" + piece).trim());
            } else {
                result.add(piece);
            }
            previous = piece;
        }
        return result;
    }

    private static String overlapSuffix(String text) {
        if (text.length() <= OVERLAP_CHARS) {
            return text;
        }
        String tail = text.substring(text.length() - OVERLAP_CHARS);
        int breakAt = indexOfBreak(tail);
        return breakAt >= 0 ? tail.substring(breakAt).trim() : tail.trim();
    }

    private static int indexOfBreak(String tail) {
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        return -1;
    }

    private static int indexOfFrom(String source, String body, int from) {
        int max = Math.min(body.length(), 80);
        String needle = body.substring(0, max);
        return source.indexOf(needle, from);
    }

    public record ParentGroup(String headingPath, List<ChunkDraft> children) {
        public String combinedBody() {
            StringBuilder builder = new StringBuilder();
            for (ChunkDraft child : children) {
                if (!builder.isEmpty()) {
                    builder.append("\n\n");
                }
                builder.append(child.body());
            }
            return builder.toString();
        }
    }

    private record Section(String headingPath, String body, int start) {}
}
