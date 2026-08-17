package io.llmplatform.infra.rag;

/** 抽取后的正文与切分策略提示。 */
public record ExtractedDocument(String text, DocumentKind kind, String title) {}
