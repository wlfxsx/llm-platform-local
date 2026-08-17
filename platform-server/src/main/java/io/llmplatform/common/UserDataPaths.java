package io.llmplatform.common;

import io.llmplatform.config.PlatformProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** 解析并创建用户数据目录。运行时数据不得写入 .me。 */
@Component
public class UserDataPaths {

    private final Path root;

    public UserDataPaths(PlatformProperties properties) {
        this.root = Path.of(properties.getPlatform().getDataDir()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path models() {
        return root.resolve("models");
    }

    public Path plugins() {
        return root.resolve("plugins");
    }

    public Path skills() {
        return root.resolve("skills");
    }

    public Path rag() {
        return root.resolve("rag");
    }

    public Path database() {
        return root.resolve("platform.db");
    }

    public void ensureDirectories() {
        try {
            Files.createDirectories(models());
            Files.createDirectories(plugins());
            Files.createDirectories(skills());
            Files.createDirectories(rag());
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建用户数据目录", ex);
        }
    }
}
