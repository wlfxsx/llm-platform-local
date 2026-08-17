package io.llmplatform.infra.hardware;

import java.util.Locale;

/** 按当前系统装配显示设备探测器。 */
public final class GpuProbes {

    private GpuProbes() {}

    /** 驱动上报设备类型的首选探测器。 */
    public static GpuProbe accelerated() {
        return new VulkanGpuProbe();
    }

    /** 缺少 Vulkan 时读取系统设备信息的回退探测器。 */
    public static GpuProbe platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return new MacSystemProfilerGpuProbe();
        }
        if (os.contains("win")) {
            return new WindowsPnpGpuProbe();
        }
        return new LinuxSysfsGpuProbe();
    }

    /** macOS 推理走 Metal，不依赖 Vulkan，直接使用系统信息即可。 */
    public static boolean prefersPlatformProbe() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
