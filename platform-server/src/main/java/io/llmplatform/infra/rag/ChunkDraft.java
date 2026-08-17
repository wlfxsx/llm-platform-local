package io.llmplatform.infra.rag;

/** 切分后的子块，带标题路径与在原文中的区间。 */
public record ChunkDraft(String headingPath, String body, int charStart, int charEnd) {

    public String prefixed(String sourceTitle) {
        String path = headingPath == null || headingPath.isBlank() ? sourceTitle : headingPath;
        return "[来源: " + path + "]\n" + body;
    }
}
