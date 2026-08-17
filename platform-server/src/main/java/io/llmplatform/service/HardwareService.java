package io.llmplatform.service;

import io.llmplatform.infra.hardware.CpuProbe;
import io.llmplatform.infra.hardware.GpuProbe;
import io.llmplatform.infra.hardware.GpuProbes;
import io.llmplatform.pojo.vo.HardwareDevice;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 汇总可用于推理的本机设备。
 *
 * <p>显示设备优先由 Vulkan 枚举，设备类型直接取驱动上报的结果； Vulkan 不可用时回退到各平台的系统信息，此时只列出物理显卡并标记为不可用。
 */
@Service
public class HardwareService {

    private final CpuProbe cpuProbe;
    private final GpuProbe acceleratedProbe;
    private final GpuProbe platformProbe;

    public HardwareService() {
        this(new CpuProbe(), GpuProbes.accelerated(), GpuProbes.platform());
    }

    HardwareService(CpuProbe cpuProbe, GpuProbe acceleratedProbe, GpuProbe platformProbe) {
        this.cpuProbe = cpuProbe;
        this.acceleratedProbe = acceleratedProbe;
        this.platformProbe = platformProbe;
    }

    public List<HardwareDevice> detect() {
        List<HardwareDevice> devices = new ArrayList<>();
        devices.add(new HardwareDevice("cpu", "cpu", cpuProbe.describe(), true, false, null));
        devices.addAll(detectGpus());
        return markRecommended(devices);
    }

    /** 解析实际用于推理的设备；未选择、设备已消失或不可用时按独显、核显、CPU 顺序回退。 */
    public HardwareDevice resolve(String deviceId) {
        List<HardwareDevice> devices = detect();
        return devices.stream()
                .filter(device -> device.available() && device.id().equals(deviceId))
                .findFirst()
                .orElseGet(() -> preferred(devices));
    }

    private List<HardwareDevice> detectGpus() {
        if (GpuProbes.prefersPlatformProbe()) {
            return platformProbe.probe();
        }

        List<HardwareDevice> devices = acceleratedProbe.probe();
        return devices.isEmpty() ? platformProbe.probe() : devices;
    }

    /** 默认顺序：可用独显优先，其次核显，最后 CPU。 */
    static HardwareDevice preferred(List<HardwareDevice> devices) {
        HardwareDevice device = pick(devices, "gpu");
        if (device == null) {
            device = pick(devices, "igpu");
        }
        return device == null ? pick(devices, "cpu") : device;
    }

    static List<HardwareDevice> markRecommended(List<HardwareDevice> devices) {
        HardwareDevice preferred = preferred(devices);
        if (preferred == null) {
            return devices;
        }

        List<HardwareDevice> result = new ArrayList<>(devices.size());
        for (HardwareDevice device : devices) {
            result.add(device.id().equals(preferred.id()) ? recommend(device) : device);
        }
        return result;
    }

    private static HardwareDevice recommend(HardwareDevice device) {
        return new HardwareDevice(
                device.id(),
                device.type(),
                device.name(),
                device.available(),
                true,
                device.unavailableReason());
    }

    private static HardwareDevice pick(List<HardwareDevice> devices, String type) {
        return devices.stream()
                .filter(device -> device.available() && type.equals(device.type()))
                .findFirst()
                .orElse(null);
    }
}
