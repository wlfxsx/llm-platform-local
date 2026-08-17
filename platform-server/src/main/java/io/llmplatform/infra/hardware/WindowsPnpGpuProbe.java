package io.llmplatform.infra.hardware;

import io.llmplatform.pojo.vo.HardwareDevice;
import java.util.ArrayList;
import java.util.List;

/**
 * Windows 回退探测：按即插即用枚举器区分物理显卡与软件显示适配器。
 *
 * <p>物理显卡由 PCI 枚举器上报，虚拟/串流显示器由 ROOT、SWD 等软件枚举器创建。 没有 Vulkan 驱动时无法判定加速类型，设备按不可用返回。
 */
public class WindowsPnpGpuProbe implements GpuProbe {

    private static final String SCRIPT =
            "Get-CimInstance Win32_VideoController | ForEach-Object {"
                    + " $enumerator = (Get-PnpDeviceProperty -InstanceId $_.PNPDeviceID"
                    + " -KeyName 'DEVPKEY_Device_EnumeratorName' -ErrorAction SilentlyContinue).Data;"
                    + " $_.Name + [char]9 + $enumerator }";

    @Override
    public List<HardwareDevice> probe() {
        return parse(CommandRunner.powershell(SCRIPT));
    }

    /** 每行为「名称 制表符 枚举器」。 */
    static List<HardwareDevice> parse(List<String> lines) {
        List<HardwareDevice> devices = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split("\t", -1);
            String name = parts.length > 0 ? parts[0].trim() : "";
            String enumerator = parts.length > 1 ? parts[1].trim() : "";
            if (name.isEmpty() || !"PCI".equalsIgnoreCase(enumerator)) {
                continue;
            }
            devices.add(
                    new HardwareDevice(
                            "gpu-" + devices.size(), "gpu", name, false, false, REASON_NO_VULKAN));
        }
        return devices;
    }
}
