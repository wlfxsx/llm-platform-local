package io.llmplatform.pojo.vo;

/** llamafile 进程状态；pid 与启动时间仅在本进程托管时有值。 */
public record LlamafileStatus(
        String state,
        String messageKey,
        boolean healthy,
        String endpoint,
        Long pid,
        Long startedAt,
        String modelId,
        String modelName) {

    public LlamafileStatus(String state, String messageKey, boolean healthy, String endpoint) {
        this(state, messageKey, healthy, endpoint, null, null, null, null);
    }
}
