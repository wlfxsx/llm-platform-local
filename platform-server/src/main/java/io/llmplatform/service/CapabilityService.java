package io.llmplatform.service;

import io.llmplatform.common.constant.CapabilityIds;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.vo.CapabilityView;
import io.llmplatform.repository.CapabilityRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 统一能力开关。优先级：全局关闭大于会话关闭，插件不能打开全局禁用的能力。 */
@Service
public class CapabilityService {

    private final CapabilityRepository capabilityRepository;
    private final SettingsService settingsService;

    public CapabilityService(
            CapabilityRepository capabilityRepository, SettingsService settingsService) {
        this.capabilityRepository = capabilityRepository;
        this.settingsService = settingsService;
        seedDefaults();
    }

    public void setGlobal(String capabilityId, boolean enabled) {
        if (CapabilityIds.CHAT.equals(capabilityId) && !enabled) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
        if (CapabilityIds.NETWORK.equals(capabilityId)
                && enabled
                && !settingsService.current().networkEnabled()) {
            throw new PlatformException("NETWORK_DISABLED", "error.networkDisabled");
        }
        capabilityRepository.upsertGlobal(capabilityId, enabled);
    }

    public List<CapabilityView> listGlobal() {
        List<CapabilityView> views = new ArrayList<>();
        for (String id : CapabilityIds.all()) {
            String reason = null;
            boolean enabled = isEnabled(id);
            // 应用级联网关闭时，联网能力对外一律视为关闭，避免开关显示与实际出网能力不一致。
            if (CapabilityIds.NETWORK.equals(id) && !settingsService.current().networkEnabled()) {
                enabled = false;
                reason = "error.networkDisabled";
            } else if (!enabled) {
                reason = "error.capabilityDisabled";
            }
            views.add(new CapabilityView(id, enabled, "global", reason));
        }
        return views;
    }

    public boolean isEnabled(String capabilityId) {
        if (CapabilityIds.CHAT.equals(capabilityId)) {
            return true;
        }
        // 应用级联网关闭时，联网能力实际不可用，不能只改列表展示而让 require() 放行。
        if (CapabilityIds.NETWORK.equals(capabilityId)
                && !settingsService.current().networkEnabled()) {
            return false;
        }
        return capabilityRepository.findGlobal(capabilityId).orElse(false);
    }

    public boolean isEnabledForSession(String sessionId, String capabilityId) {
        if (!isEnabled(capabilityId)) {
            return false;
        }
        return capabilityRepository.findSession(sessionId, capabilityId).orElse(true);
    }

    public boolean isEnabledForPlugin(String pluginId, String capabilityId) {
        if (!isEnabled(capabilityId)) {
            return false;
        }
        return capabilityRepository.findPlugin(pluginId, capabilityId).orElse(true);
    }

    public void setSession(String sessionId, String capabilityId, boolean enabled) {
        capabilityRepository.upsertSession(sessionId, capabilityId, enabled);
    }

    /** 会话被删除时一并清掉其能力覆盖。 */
    public void clearSession(String sessionId) {
        capabilityRepository.deleteSession(sessionId);
    }

    public void setPlugin(String pluginId, String capabilityId, boolean enabled) {
        if (enabled && !isEnabled(capabilityId)) {
            throw new PlatformException(
                    "CAPABILITY_DISABLED", "error.capabilityDisabled", Map.of("id", capabilityId));
        }
        capabilityRepository.upsertPlugin(pluginId, capabilityId, enabled);
    }

    public void require(String capabilityId) {
        if (!isEnabled(capabilityId)) {
            throw new PlatformException(
                    "CAPABILITY_DISABLED", "error.capabilityDisabled", Map.of("id", capabilityId));
        }
    }

    private void seedDefaults() {
        for (String id : CapabilityIds.all()) {
            boolean enabled = CapabilityIds.CHAT.equals(id) || CapabilityIds.CONTEXT.equals(id);
            capabilityRepository.insertDefault(id, enabled);
        }
    }
}
