package io.llmplatform.infra.hardware;

import java.util.List;
import java.util.Locale;

/** 读取处理器型号与逻辑核心数。 */
public class CpuProbe {

    public String describe() {
        int threads = Runtime.getRuntime().availableProcessors();
        String name = model();
        if (name.isBlank()) {
            name = "CPU";
        }
        return name + " · " + threads + "T";
    }

    private static String model() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return first(
                    CommandRunner.powershell(
                            "(Get-CimInstance Win32_Processor |"
                                    + " Select-Object -First 1 -ExpandProperty Name)"));
        }
        if (os.contains("mac")) {
            return first(CommandRunner.run("sysctl", "-n", "machdep.cpu.brand_string"));
        }
        String line = first(CommandRunner.run("sh", "-c", "grep -m1 'model name' /proc/cpuinfo"));
        int colon = line.indexOf(':');
        return colon >= 0 ? line.substring(colon + 1).trim() : line;
    }

    private static String first(List<String> lines) {
        return lines.isEmpty() ? "" : lines.getFirst().trim();
    }
}
