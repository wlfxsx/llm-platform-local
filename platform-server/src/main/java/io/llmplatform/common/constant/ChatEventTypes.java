package io.llmplatform.common.constant;

/** 对话流与 WebSocket 使用的稳定事件类型。 */
public final class ChatEventTypes {

    public static final String COMPRESS_STARTED = "context.compress.started";
    public static final String COMPRESS_COMPLETED = "context.compress.completed";
    public static final String COMPRESS_ERROR = "context.compress.error";

    private ChatEventTypes() {}
}
