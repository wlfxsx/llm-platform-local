package io.llmplatform.infra.hardware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.pojo.vo.HardwareDevice;
import java.util.ArrayList;
import java.util.List;

/**
 * macOS 探测：system_profiler 直接给出总线归属。
 *
 * <p>sppci_bus 为 spdisplays_builtin 表示核显或统一内存 GPU，其余为 PCIe 独显。 macOS 推理走 Metal，不依赖
 * Vulkan，因此设备按可用返回。
 */
public class MacSystemProfilerGpuProbe implements GpuProbe {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public List<HardwareDevice> probe() {
        List<String> output = CommandRunner.run("system_profiler", "-json", "SPDisplaysDataType");
        return parse(String.join("\n", output));
    }

    static List<HardwareDevice> parse(String json) {
        List<HardwareDevice> devices = new ArrayList<>();
        if (json.isBlank()) {
            return devices;
        }

        try {
            JsonNode items = MAPPER.readTree(json).path("SPDisplaysDataType");
            for (JsonNode item : items) {
                String name = item.path("sppci_model").asText("").trim();
                if (name.isEmpty()) {
                    continue;
                }
                boolean builtin = "spdisplays_builtin".equals(item.path("sppci_bus").asText(""));
                String type = builtin ? "igpu" : "gpu";
                devices.add(
                        new HardwareDevice(
                                type + "-" + devices.size(), type, name, true, false, null));
            }
        } catch (Exception ex) {
            return List.of();
        }
        return devices;
    }
}
