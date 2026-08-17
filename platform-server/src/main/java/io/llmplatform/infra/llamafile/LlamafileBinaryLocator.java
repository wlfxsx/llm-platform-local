package io.llmplatform.infra.llamafile;

import io.llmplatform.common.error.PlatformException;
import io.llmplatform.config.PlatformProperties;
import io.llmplatform.pojo.entity.AppSettings;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 解析本机 llamafile 可执行文件，供对话与辅助 embedding/rerank 进程共用。 */
@Component
public class LlamafileBinaryLocator {

    private final PlatformProperties properties;

    public LlamafileBinaryLocator(PlatformProperties properties) {
        this.properties = properties;
    }

    public Path resolve(AppSettings settings) {
        String fileName =
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                        ? "llamafile.exe"
                        : "llamafile";
        Set<Path> candidates = new LinkedHashSet<>();
        if (settings.llamafileBinary() != null && !settings.llamafileBinary().isBlank()) {
            candidates.add(Path.of(settings.llamafileBinary()));
        }
        if (!properties.getLlamafile().getBinaryPath().isBlank()) {
            candidates.add(Path.of(properties.getLlamafile().getBinaryPath()));
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        candidates.add(cwd.resolve(fileName));
        candidates.add(cwd.resolve("runtime").resolve(fileName));
        Path jarDir = locateJarDirectory();
        if (jarDir != null) {
            candidates.add(jarDir.resolve(fileName));
            candidates.add(jarDir.resolve("runtime").resolve(fileName));
            Path parent = jarDir.getParent();
            if (parent != null) {
                candidates.add(parent.resolve("runtime").resolve(fileName));
            }
        }
        Path walk = cwd;
        for (int i = 0; i < 8 && walk != null; i++) {
            candidates.add(walk.resolve(".me").resolve("files").resolve(fileName));
            walk = walk.getParent();
        }
        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath().normalize();
            }
        }
        throw new PlatformException("LLAMAFILE_FAILED", "error.llamafileFailed");
    }

    public Path locateJarDirectory() {
        try {
            CodeSource source = LlamafileBinaryLocator.class.getProtectionDomain().getCodeSource();
            if (source == null) {
                return null;
            }
            URL location = source.getLocation();
            if (location == null) {
                return null;
            }
            Path path = Path.of(location.toURI());
            if (Files.isRegularFile(path)) {
                return path.getParent();
            }
            if (Files.isDirectory(path)) {
                return path;
            }
        } catch (URISyntaxException ignored) {
            // 类路径异常时回退到其它候选路径。
        }
        return null;
    }
}
