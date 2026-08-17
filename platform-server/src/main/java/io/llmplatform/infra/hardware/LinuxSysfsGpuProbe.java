package io.llmplatform.infra.hardware;

import io.llmplatform.pojo.vo.HardwareDevice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Linux 回退探测：读取 sysfs 中的 PCI 设备类别码。
 *
 * <p>类别码 0x03 为显示控制器，只有真实 PCI 设备会出现在 /sys/bus/pci/devices 下。 没有 Vulkan 驱动时无法判定加速类型，设备按不可用返回。
 */
public class LinuxSysfsGpuProbe implements GpuProbe {

    private static final String PCI_CLASS_DISPLAY = "0x03";

    private final Path pciRoot;

    public LinuxSysfsGpuProbe() {
        this(Path.of("/sys/bus/pci/devices"));
    }

    LinuxSysfsGpuProbe(Path pciRoot) {
        this.pciRoot = pciRoot;
    }

    @Override
    public List<HardwareDevice> probe() {
        List<HardwareDevice> devices = new ArrayList<>();
        if (!Files.isDirectory(pciRoot)) {
            return devices;
        }

        try (Stream<Path> entries = Files.list(pciRoot)) {
            for (Path device : entries.sorted().toList()) {
                if (!isDisplayController(device)) {
                    continue;
                }
                devices.add(
                        new HardwareDevice(
                                "gpu-" + devices.size(),
                                "gpu",
                                describe(device),
                                false,
                                false,
                                REASON_NO_VULKAN));
            }
        } catch (IOException ex) {
            return List.of();
        }
        return devices;
    }

    private static boolean isDisplayController(Path device) {
        String classCode = read(device.resolve("class"));
        return classCode.toLowerCase(Locale.ROOT).startsWith(PCI_CLASS_DISPLAY);
    }

    /** sysfs 只提供厂商和设备编号，可读名称交给 lspci，缺少时退回编号。 */
    private static String describe(Path device) {
        String address = device.getFileName().toString();
        List<String> output = CommandRunner.run("lspci", "-mm", "-s", address);
        String name = output.isEmpty() ? "" : parseLspci(output.getFirst());
        if (!name.isBlank()) {
            return name;
        }
        String vendor = read(device.resolve("vendor")).replace("0x", "");
        String model = read(device.resolve("device")).replace("0x", "");
        return "PCI " + address + " [" + vendor + ":" + model + "]";
    }

    /** lspci -mm 输出形如：01:00.0 "VGA" "NVIDIA" "AD107" -rev "GeForce RTX 4060"。 */
    static String parseLspci(String line) {
        List<String> quoted = new ArrayList<>();
        int start = line.indexOf('"');
        while (start >= 0) {
            int end = line.indexOf('"', start + 1);
            if (end < 0) {
                break;
            }
            quoted.add(line.substring(start + 1, end));
            start = line.indexOf('"', end + 1);
        }
        if (quoted.size() < 3) {
            return "";
        }
        return (quoted.get(1) + " " + quoted.get(2)).trim();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file).trim();
        } catch (IOException ex) {
            return "";
        }
    }
}
