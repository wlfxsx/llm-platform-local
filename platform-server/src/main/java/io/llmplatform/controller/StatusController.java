package io.llmplatform.controller;

import io.llmplatform.common.error.PlatformException;
import io.llmplatform.infra.llamafile.LlamafileManager;
import io.llmplatform.pojo.dto.LlamafileStartRequest;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.pojo.vo.LlamafileStatus;
import io.llmplatform.pojo.vo.PlatformStatus;
import io.llmplatform.pojo.vo.RemoteInferenceStatus;
import io.llmplatform.pojo.vo.RemoteModelView;
import io.llmplatform.repository.VectorExtensionLoader;
import io.llmplatform.service.ModelConfigProfileService;
import io.llmplatform.service.RemoteModelService;
import io.llmplatform.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 服务与模型就绪状态。 */
@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final LlamafileManager llamafileManager;
    private final VectorExtensionLoader vectorExtensionLoader;
    private final ModelConfigProfileService profileService;
    private final SettingsService settingsService;
    private final RemoteModelService remoteModelService;

    public StatusController(
            LlamafileManager llamafileManager,
            VectorExtensionLoader vectorExtensionLoader,
            ModelConfigProfileService profileService,
            SettingsService settingsService,
            RemoteModelService remoteModelService) {
        this.llamafileManager = llamafileManager;
        this.vectorExtensionLoader = vectorExtensionLoader;
        this.profileService = profileService;
        this.settingsService = settingsService;
        this.remoteModelService = remoteModelService;
    }

    @GetMapping
    public PlatformStatus status() {
        AppSettings settings = settingsService.current();
        LlamafileStatus local = llamafileManager.status();
        RemoteInferenceStatus remote = remoteStatus(settings);
        boolean ready = settings.remoteChat() ? remote.ready() : local.healthy();
        return new PlatformStatus(
                local,
                remote,
                settings.chatProvider(),
                ready,
                vectorExtensionLoader.loaded(),
                vectorExtensionLoader.version());
    }

    /** 指定策略时先把策略参数写入当前模型，再拉起进程，保证运行参数与所选策略一致。 */
    @PostMapping("/llamafile/start")
    public LlamafileStatus start(@RequestBody(required = false) LlamafileStartRequest request) {
        if (settingsService.current().remoteChat()) {
            throw new PlatformException("REMOTE_CHAT_DISABLED", "error.remoteChatDisabled");
        }
        if (request != null && request.profileId() != null && !request.profileId().isBlank()) {
            profileService.applyToCurrentModel(request.profileId());
        }
        return llamafileManager.start();
    }

    @PostMapping("/llamafile/stop")
    public LlamafileStatus stop() {
        llamafileManager.stop();
        return llamafileManager.status();
    }

    private RemoteInferenceStatus remoteStatus(AppSettings settings) {
        String id = settings.currentRemoteModelId();
        if (id == null || id.isBlank()) {
            return new RemoteInferenceStatus("stopped", "status.remoteNotReady", false, "", "");
        }
        try {
            RemoteModelView view = remoteModelService.get(id);
            boolean ready =
                    settings.networkEnabled()
                            && view.hasApiKey()
                            && (!settings.remoteChat() || remoteModelService.currentReady());
            return new RemoteInferenceStatus(
                    ready ? "ready" : "stopped",
                    ready ? "status.remoteReady" : "status.remoteNotReady",
                    ready,
                    view.id(),
                    view.name());
        } catch (PlatformException ex) {
            return new RemoteInferenceStatus("stopped", "status.remoteNotReady", false, id, "");
        }
    }
}
