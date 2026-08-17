package io.llmplatform.infra.hardware;

import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;

import io.llmplatform.pojo.vo.HardwareDevice;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通过 Vulkan 枚举可计算的物理设备。
 *
 * <p>设备类型由显卡驱动上报（独显 / 核显 / 虚拟 GPU / 软件光栅），无需按名称判断； 虚拟显示适配器没有 Vulkan 驱动，不会出现在枚举结果里。
 */
public class VulkanGpuProbe implements GpuProbe {

    private static final Logger log = LoggerFactory.getLogger(VulkanGpuProbe.class);

    static {
        // 创建 Vulkan 实例时会一次性缓存全部扩展与函数指针名，默认 64KB 线程栈不够用。
        Configuration.STACK_SIZE.set(1024);
    }

    @Override
    public List<HardwareDevice> probe() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo application =
                    VkApplicationInfo.calloc(stack)
                            .sType$Default()
                            .pApplicationName(stack.UTF8("llm-platform"))
                            .apiVersion(VK_API_VERSION_1_0);
            VkInstanceCreateInfo createInfo =
                    VkInstanceCreateInfo.calloc(stack)
                            .sType$Default()
                            .pApplicationInfo(application);
            PointerBuffer handle = stack.mallocPointer(1);
            if (vkCreateInstance(createInfo, null, handle) != VK_SUCCESS) {
                return List.of();
            }

            VkInstance instance = new VkInstance(handle.get(0), createInfo);
            try {
                return enumerate(stack, instance);
            } finally {
                vkDestroyInstance(instance, null);
            }
        } catch (Throwable ex) {
            // 缺少 Vulkan 运行时或驱动异常时降级，由平台探测器兜底。
            log.info("Vulkan 探测不可用：{}", ex.toString());
            return List.of();
        }
    }

    private static List<HardwareDevice> enumerate(MemoryStack stack, VkInstance instance) {
        IntBuffer count = stack.mallocInt(1);
        if (vkEnumeratePhysicalDevices(instance, count, null) != VK_SUCCESS || count.get(0) == 0) {
            return List.of();
        }

        PointerBuffer handles = stack.mallocPointer(count.get(0));
        if (vkEnumeratePhysicalDevices(instance, count, handles) != VK_SUCCESS) {
            return List.of();
        }

        List<HardwareDevice> devices = new ArrayList<>(count.get(0));
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
        for (int i = 0; i < count.get(0); i++) {
            vkGetPhysicalDeviceProperties(
                    new VkPhysicalDevice(handles.get(i), instance), properties);
            String type = mapType(properties.deviceType());
            if (type == null) {
                // 虚拟 GPU 与软件光栅设备无法提供推理加速，直接忽略。
                continue;
            }
            devices.add(
                    new HardwareDevice(
                            type + "-" + devices.size(),
                            type,
                            properties.deviceNameString(),
                            true,
                            false,
                            null));
        }
        return devices;
    }

    private static String mapType(int deviceType) {
        return switch (deviceType) {
            case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> "gpu";
            case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> "igpu";
            default -> null;
        };
    }
}
