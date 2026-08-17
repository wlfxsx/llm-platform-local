package io.llmplatform.pojo.vo;

/** 会话列表项；标题为空时由客户端显示占位文案。 */
public record SessionView(String id, String title, long createdAt, long updatedAt) {}
