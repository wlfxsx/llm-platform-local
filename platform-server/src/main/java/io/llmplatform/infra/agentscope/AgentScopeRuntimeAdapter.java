package io.llmplatform.infra.agentscope;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.llmplatform.infra.llamafile.LlamafileManager;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.service.RemoteModelService;
import io.llmplatform.service.RemoteModelService.ResolvedRemoteModel;
import io.llmplatform.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 将当前推理后端的 OpenAI 兼容接口接入 AgentScope。编排层仍由平台控制能力开关。 */
@Component
public class AgentScopeRuntimeAdapter {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeRuntimeAdapter.class);
    private final LlamafileManager llamafileManager;
    private final SettingsService settingsService;
    private final RemoteModelService remoteModelService;
    private volatile OpenAIChatModel chatModel;
    private volatile String boundKey = "";

    public AgentScopeRuntimeAdapter(
            LlamafileManager llamafileManager,
            SettingsService settingsService,
            RemoteModelService remoteModelService) {
        this.llamafileManager = llamafileManager;
        this.settingsService = settingsService;
        this.remoteModelService = remoteModelService;
    }

    public synchronized OpenAIChatModel chatModel() {
        String key = bindingKey();
        if (chatModel == null || !key.equals(boundKey)) {
            chatModel = build(key);
            boundKey = key;
            log.info("已创建 AgentScope OpenAI 兼容模型适配器");
        }
        return chatModel;
    }

    public synchronized void reset() {
        chatModel = null;
        boundKey = "";
    }

    private OpenAIChatModel build(String key) {
        AppSettings settings = settingsService.current();
        if (settings.remoteChat()) {
            ResolvedRemoteModel remote = remoteModelService.resolveCurrent();
            return OpenAIChatModel.builder()
                    .modelName(remote.modelName())
                    .baseUrl(remote.baseUrl())
                    .apiKey(remote.apiKey())
                    .stream(true)
                    .build();
        }
        return OpenAIChatModel.builder()
                .modelName("local-llamafile")
                .baseUrl(llamafileManager.endpoint() + "/v1")
                .apiKey("sk-local")
                .stream(true)
                .build();
    }

    private String bindingKey() {
        AppSettings settings = settingsService.current();
        if (settings.remoteChat()) {
            return "remote:" + settings.currentRemoteModelId();
        }
        return "local:" + llamafileManager.endpoint();
    }
}
