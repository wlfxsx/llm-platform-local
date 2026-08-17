package io.llmplatform.controller;

import io.llmplatform.pojo.dto.EnabledUpdateRequest;
import io.llmplatform.pojo.vo.CapabilityView;
import io.llmplatform.service.CapabilityService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 全局、会话与插件能力开关。 */
@RestController
@RequestMapping("/api")
public class CapabilityController {

    private final CapabilityService capabilityService;

    public CapabilityController(CapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    @GetMapping("/capabilities")
    public List<CapabilityView> list() {
        return capabilityService.listGlobal();
    }

    @PutMapping("/capabilities/{id}")
    public void updateGlobal(
            @PathVariable String id, @Valid @RequestBody EnabledUpdateRequest body) {
        capabilityService.setGlobal(id, Boolean.TRUE.equals(body.enabled()));
    }

    @PutMapping("/sessions/{sessionId}/capabilities/{capabilityId}")
    public void updateSession(
            @PathVariable String sessionId,
            @PathVariable String capabilityId,
            @Valid @RequestBody EnabledUpdateRequest body) {
        capabilityService.setSession(sessionId, capabilityId, Boolean.TRUE.equals(body.enabled()));
    }

    @PutMapping("/plugins/{pluginId}/capabilities/{capabilityId}")
    public void updatePlugin(
            @PathVariable String pluginId,
            @PathVariable String capabilityId,
            @Valid @RequestBody EnabledUpdateRequest body) {
        capabilityService.setPlugin(pluginId, capabilityId, Boolean.TRUE.equals(body.enabled()));
    }
}
