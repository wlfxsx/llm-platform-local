package io.llmplatform.infra.llm;

import io.llmplatform.infra.llamafile.LlamafileChatClient;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.pojo.vo.ModelConfigView;
import io.llmplatform.service.SettingsService;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 按 chatProvider 在本地 llamafile 与远程 OpenAI 兼容服务之间分流。 */
@Component
@Primary
public class RoutingChatModelClient implements ChatModelClient {

    private final SettingsService settingsService;
    private final LlamafileChatClient local;
    private final OpenAiCompatibleChatClient remote;

    public RoutingChatModelClient(
            SettingsService settingsService,
            LlamafileChatClient local,
            OpenAiCompatibleChatClient remote) {
        this.settingsService = settingsService;
        this.local = local;
        this.remote = remote;
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        return active().complete(messages);
    }

    @Override
    public String completeNonStream(
            List<ChatMessage> messages,
            ModelConfigView config,
            Double temperatureOverride,
            Integer maxTokensOverride) {
        return active().completeNonStream(messages, config, temperatureOverride, maxTokensOverride);
    }

    @Override
    public void stream(List<ChatMessage> messages, Consumer<String> onDelta) {
        active().stream(messages, onDelta);
    }

    @Override
    public void stream(
            List<ChatMessage> messages, ModelConfigView config, Consumer<String> onDelta) {
        active().stream(messages, config, onDelta);
    }

    private ChatModelClient active() {
        return settingsService.current().remoteChat() ? remote : local;
    }
}
