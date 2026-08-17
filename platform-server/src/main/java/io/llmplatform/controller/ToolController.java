package io.llmplatform.controller;

import io.llmplatform.pojo.vo.PlatformTool;
import io.llmplatform.service.ToolService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已注册工具只读列表。 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolService toolService;

    public ToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping
    public List<PlatformTool> list() {
        return toolService.list();
    }
}
