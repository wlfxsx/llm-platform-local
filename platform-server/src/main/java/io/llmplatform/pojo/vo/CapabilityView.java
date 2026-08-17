package io.llmplatform.pojo.vo;

/** 能力开关快照。 */
public record CapabilityView(String id, boolean enabled, String source, String unavailableReason) {}
