package io.llmplatform.controller;

import io.llmplatform.pojo.vo.HardwareDevice;
import io.llmplatform.service.HardwareService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 推理硬件探测。 */
@RestController
@RequestMapping("/api/hardware")
public class HardwareController {

    private final HardwareService hardwareService;

    public HardwareController(HardwareService hardwareService) {
        this.hardwareService = hardwareService;
    }

    @GetMapping
    public List<HardwareDevice> list() {
        return hardwareService.detect();
    }
}
