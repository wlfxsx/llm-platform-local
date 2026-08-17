package io.llmplatform.infra.hardware;

import java.util.Locale;

/**
 * 把显卡映射为 llamafile 的 {@code --gpu} 后端取值。
 *
 * <p>llamafile 接受 auto、apple、amd、nvidia、vulkan 和 disable。显式指定后端时探测失败会直接退出进程， 因此只对自带预编译库的 NVIDIA 走
 * CUDA、Apple 走 Metal，其余显卡统一走驱动自带的 Vulkan： AMD 的 ROCm 与 Intel 核显在多数 Windows 机器上并不可用。
 */
public final class GpuVendors {

    private GpuVendors() {}

    /** 返回 --gpu 取值；仅用于显卡，CPU 由调用方单独处理。 */
    public static String backendOf(String deviceName) {
        return switch (vendorOf(deviceName)) {
            case "nvidia" -> "nvidia";
            case "apple" -> "apple";
            default -> "vulkan";
        };
    }

    static String vendorOf(String deviceName) {
        String name = deviceName == null ? "" : deviceName.toLowerCase(Locale.ROOT);
        if (name.contains("nvidia")
                || name.contains("geforce")
                || name.contains("rtx")
                || name.contains("quadro")
                || name.contains("tesla")) {
            return "nvidia";
        }
        if (name.contains("apple")) {
            return "apple";
        }
        if (name.contains("amd") || name.contains("radeon")) {
            return "amd";
        }
        return "";
    }
}
