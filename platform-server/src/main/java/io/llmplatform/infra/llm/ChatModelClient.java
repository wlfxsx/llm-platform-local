package io.llmplatform.infra.llm;

import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.pojo.vo.ModelConfigView;
import java.util.List;
import java.util.function.Consumer;

/** 本地与远程聊天客户端的统一入口。 */
public interface ChatModelClient {

    String complete(List<ChatMessage> messages);

    String completeNonStream(
            List<ChatMessage> messages,
            ModelConfigView config,
            Double temperatureOverride,
            Integer maxTokensOverride);

    void stream(List<ChatMessage> messages, Consumer<String> onDelta);

    void stream(List<ChatMessage> messages, ModelConfigView config, Consumer<String> onDelta);
}
