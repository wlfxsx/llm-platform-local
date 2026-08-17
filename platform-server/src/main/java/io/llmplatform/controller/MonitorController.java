package io.llmplatform.controller;

import io.llmplatform.pojo.vo.RuntimeMonitorSnapshot;
import io.llmplatform.service.RuntimeMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本机资源与模型运行快照，供监控页轮询。 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final RuntimeMonitorService runtimeMonitorService;

    public MonitorController(RuntimeMonitorService runtimeMonitorService) {
        this.runtimeMonitorService = runtimeMonitorService;
    }

    @GetMapping("/snapshot")
    public RuntimeMonitorSnapshot snapshot() {
        return runtimeMonitorService.snapshot();
    }
}
