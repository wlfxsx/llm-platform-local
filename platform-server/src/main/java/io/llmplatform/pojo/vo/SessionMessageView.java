package io.llmplatform.pojo.vo;

/** 会话历史消息；按会话内序号正序返回，序号用于定位撤回与修改的截断点。 */
public record SessionMessageView(
        String id, String role, String content, long createdAt, int sequenceNo) {}
