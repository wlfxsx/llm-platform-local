package io.llmplatform.controller;

import io.llmplatform.pojo.vo.ShutdownAccepted;
import io.llmplatform.service.ShutdownService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 桌面退出时调用：停止 llamafile 并关闭控制面。 */
@RestController
@RequestMapping("/api/shutdown")
public class ShutdownController {

    private final ShutdownService shutdownService;

    public ShutdownController(ShutdownService shutdownService) {
        this.shutdownService = shutdownService;
    }

    @PostMapping
    public ShutdownAccepted shutdown() {
        return shutdownService.shutdown();
    }
}
