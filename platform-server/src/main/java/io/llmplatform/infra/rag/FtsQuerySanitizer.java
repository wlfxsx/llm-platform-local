package io.llmplatform.infra.rag;

/** 去掉 FTS5 运算符，避免用户问句被当成查询语法。 */
public final class FtsQuerySanitizer {

    private FtsQuerySanitizer() {}

    public static String sanitize(String query) {
        if (query == null) {
            return "";
        }
        String cleaned = query.replaceAll("[^\\p{L}\\p{N}\\s]+", " ").trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        if (cleaned.isBlank()) {
            return "";
        }
        return "\"" + cleaned.replace("\"", "") + "\"";
    }
}
