package io.llmplatform.infra.hardware;

import io.llmplatform.pojo.vo.HardwareDevice;
import java.util.List;

/** 显示设备探测器。实现必须依据系统上报的结构化信息，不得按设备名猜测。 */
public interface GpuProbe {

    /** 缺少 Vulkan 运行时，显卡无法用于推理加速。 */
    String REASON_NO_VULKAN = "noVulkanRuntime";

    List<HardwareDevice> probe();
}
