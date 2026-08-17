package io.llmplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.UserDataPaths;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.entity.PluginCapabilityDecl;
import io.llmplatform.pojo.entity.PluginManifest;
import io.llmplatform.pojo.entity.PluginRecord;
import io.llmplatform.repository.PluginRepository;
import io.llmplatform.repository.PluginRepository.StoredPlugin;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 无后缀压缩包的导入、导出、删除与热插拔宿主骨架。 */
@Service
public class PluginService {

    private final PluginRepository pluginRepository;
    private final UserDataPaths paths;
    private final ObjectMapper objectMapper;
    private final CapabilityService capabilities;

    public PluginService(
            PluginRepository pluginRepository,
            UserDataPaths paths,
            ObjectMapper objectMapper,
            CapabilityService capabilities) {
        this.pluginRepository = pluginRepository;
        this.paths = paths;
        this.objectMapper = objectMapper;
        this.capabilities = capabilities;
    }

    public List<PluginRecord> list() {
        return pluginRepository.findAll().stream().map(this::toRecord).toList();
    }

    /** 数据库主记录与能力声明保持事务一致；压缩包解压属于文件系统副作用，失败时可能留下未注册目录， 但不会产生可加载的半成品数据库记录。 */
    @Transactional
    public PluginRecord importPackage(Path archive) {
        paths.ensureDirectories();
        PluginManifest manifest = readManifest(archive);
        if (manifest.id() == null || manifest.id().isBlank()) {
            throw new PlatformException("PLUGIN_INVALID", "error.pluginInvalid");
        }
        if (pluginRepository.exists(manifest.id())) {
            throw new PlatformException("PLUGIN_EXISTS", "error.pluginExists");
        }
        Path target = paths.plugins().resolve(manifest.id());
        unzip(archive, target);
        try {
            StoredPlugin plugin =
                    new StoredPlugin(
                            manifest.id(),
                            manifest.name(),
                            manifest.version(),
                            false,
                            target.toString(),
                            objectMapper.writeValueAsString(manifest));
            pluginRepository.insert(plugin, System.currentTimeMillis());
            manifest.capabilities()
                    .forEach(
                            (id, decl) ->
                                    capabilities.setPlugin(
                                            manifest.id(),
                                            id,
                                            decl.enabledByDefault() && capabilities.isEnabled(id)));
            return toRecord(plugin);
        } catch (IOException ex) {
            throw new PlatformException("PLUGIN_INVALID", "error.pluginInvalid");
        }
    }

    public Path exportPackage(String id) {
        PluginRecord plugin = toRecord(require(id));
        try {
            Path output = Files.createTempFile("plugin-" + id, "");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
                Path root = Path.of(plugin.directory());
                try (var walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                            .forEach(
                                    file -> {
                                        try {
                                            zip.putNextEntry(
                                                    new ZipEntry(
                                                            root.relativize(file)
                                                                    .toString()
                                                                    .replace('\\', '/')));
                                            Files.copy(file, zip);
                                            zip.closeEntry();
                                        } catch (IOException ex) {
                                            throw new PlatformException(
                                                    "FILE_COPY_FAILED", "error.fileCopyFailed");
                                        }
                                    });
                }
            }
            return output;
        } catch (IOException ex) {
            throw new PlatformException("FILE_COPY_FAILED", "error.fileCopyFailed");
        }
    }

    public void setEnabled(String id, boolean enabled) {
        require(id);
        pluginRepository.updateEnabled(id, enabled);
    }

    public void delete(String id) {
        StoredPlugin plugin = require(id);
        pluginRepository.deleteById(id);
        try {
            Path dir = Path.of(plugin.directory());
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted((a, b) -> b.compareTo(a))
                            .forEach(
                                    path -> {
                                        try {
                                            Files.deleteIfExists(path);
                                        } catch (IOException ignored) {
                                            // 尽力删除插件目录。
                                        }
                                    });
                }
            }
        } catch (IOException ignored) {
            // 数据库记录已删除。
        }
    }

    public Map<String, Object> validate(String id) {
        PluginManifest manifest = parseManifest(require(id).manifestJson());
        boolean ok = true;
        String reason = null;
        for (Map.Entry<String, PluginCapabilityDecl> entry : manifest.capabilities().entrySet()) {
            if (entry.getValue().required() && !capabilities.isEnabled(entry.getKey())) {
                ok = false;
                reason = entry.getKey();
            }
        }
        return Map.of("id", id, "valid", ok, "missingCapability", reason == null ? "" : reason);
    }

    public PluginManifest manifest(String id) {
        return parseManifest(require(id).manifestJson());
    }

    private StoredPlugin require(String id) {
        return pluginRepository
                .findById(id)
                .orElseThrow(() -> new PlatformException("NOT_FOUND", "error.notFound"));
    }

    private PluginRecord toRecord(StoredPlugin plugin) {
        String status = plugin.enabled() ? "ready" : "stopped";
        if (plugin.enabled()) {
            PluginManifest manifest = parseManifest(plugin.manifestJson());
            for (Map.Entry<String, PluginCapabilityDecl> entry :
                    manifest.capabilities().entrySet()) {
                if (entry.getValue().required() && !capabilities.isEnabled(entry.getKey())) {
                    status = "waiting-dependency";
                }
            }
        }
        return plugin.toRecord(status);
    }

    private PluginManifest parseManifest(String json) {
        try {
            return PluginManifest.fromJson(objectMapper.readTree(json));
        } catch (IOException ex) {
            throw new PlatformException("PLUGIN_INVALID", "error.pluginInvalid");
        }
    }

    private PluginManifest readManifest(Path archive) {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("plugin.json".equals(entry.getName())
                        || entry.getName().endsWith("/plugin.json")) {
                    JsonNode node = objectMapper.readTree(zip.readAllBytes());
                    return PluginManifest.fromJson(node);
                }
            }
        } catch (IOException ex) {
            throw new PlatformException("PLUGIN_INVALID", "error.pluginInvalid");
        }
        throw new PlatformException("PLUGIN_INVALID", "error.pluginInvalid");
    }

    private void unzip(Path archive, Path target) {
        try {
            Files.createDirectories(target);
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path out = target.resolve(entry.getName()).normalize();
                    // 规范化后必须仍位于插件目录内，阻止含 ../ 的条目覆盖任意本机文件。
                    if (!out.startsWith(target)) {
                        throw new PlatformException("PLUGIN_INVALID", "error.pluginInvalid");
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        try (OutputStream output = Files.newOutputStream(out)) {
                            zip.transferTo(output);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new PlatformException("PLUGIN_INVALID", "error.pluginInvalid");
        }
    }
}
