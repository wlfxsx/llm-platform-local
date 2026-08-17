package io.llmplatform.repository;

import io.llmplatform.common.UserDataPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 尝试加载 SQLite 向量扩展。失败时保持暴力检索降级，不阻止应用启动。 */
@Component
public class VectorExtensionLoader {

    private static final Logger log = LoggerFactory.getLogger(VectorExtensionLoader.class);
    private final boolean loaded;
    private final String version;

    public VectorExtensionLoader(DataSource dataSource, UserDataPaths paths) {
        // 用户数据目录优先，工作目录仅用于开发机放置 native 库；都找不到则继续暴力检索。
        Path library = findLibrary(paths.root().resolve("native"));
        if (library == null) {
            library = findLibrary(Path.of(System.getProperty("user.dir")).resolve("native"));
        }
        if (library == null) {
            this.loaded = false;
            this.version = "brute-force";
            log.info("未找到向量扩展，使用暴力检索");
            return;
        }
        boolean success = false;
        String loadedVersion = "brute-force";
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            String escaped = library.toAbsolutePath().toString().replace("'", "''");
            statement.execute("SELECT load_extension('" + escaped + "')");
            success = true;
            loadedVersion = library.getFileName().toString();
            log.info("已加载向量扩展 {}", loadedVersion);
        } catch (Exception ex) {
            log.info("向量扩展加载失败，使用暴力检索");
        }
        this.loaded = success;
        this.version = loadedVersion;
    }

    public boolean loaded() {
        return loaded;
    }

    public String version() {
        return version;
    }

    /** 按当前操作系统文件名探测常见 sqlite-vec 产物，找不到则返回空由调用方降级。 */
    private Path findLibrary(Path dir) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] names;
        if (os.contains("win")) {
            names = new String[] {"vec0.dll", "sqlite_vec.dll", "sqlite-vec.dll"};
        } else if (os.contains("mac")) {
            names = new String[] {"vec0.dylib", "libvec0.dylib", "sqlite_vec.dylib"};
        } else {
            names = new String[] {"vec0.so", "libvec0.so", "sqlite_vec.so"};
        }
        for (String name : names) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
