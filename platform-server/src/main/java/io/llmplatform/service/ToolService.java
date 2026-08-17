package io.llmplatform.service;

import io.llmplatform.common.constant.CapabilityIds;
import io.llmplatform.pojo.vo.PlatformTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 平台工具注册表。来源可以是系统、插件或 MCP。 */
@Service
public class ToolService {

    private final CapabilityService capabilities;
    private final Map<String, PlatformTool> tools = new ConcurrentHashMap<>();

    public ToolService(CapabilityService capabilities) {
        this.capabilities = capabilities;
    }

    public void register(PlatformTool tool) {
        tools.put(tool.name(), tool);
    }

    public void unregister(String name) {
        tools.remove(name);
    }

    public List<PlatformTool> list() {
        return new ArrayList<>(tools.values());
    }

    public List<PlatformTool> enabledFor(String sessionId) {
        if (!capabilities.isEnabledForSession(sessionId, CapabilityIds.TOOLS)) {
            return List.of();
        }
        return list().stream().filter(PlatformTool::enabled).toList();
    }
}
