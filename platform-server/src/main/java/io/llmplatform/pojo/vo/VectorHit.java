package io.llmplatform.pojo.vo;

/** 向量检索命中。 */
public record VectorHit(String id, String payload, double score) {}
