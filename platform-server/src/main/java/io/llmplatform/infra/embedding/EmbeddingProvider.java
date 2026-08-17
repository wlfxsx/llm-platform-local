package io.llmplatform.infra.embedding;

/** 可配置的本地或远程嵌入提供者。 */
public interface EmbeddingProvider {

    String id();

    float[] embed(String text);
}
