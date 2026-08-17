package io.llmplatform.infra.llamafile;

/** Token 计数器；测试或端点不可用时由实现自行降级到保守估算。 */
public interface TokenCounter {

    int count(String text);

    boolean accurate();
}
