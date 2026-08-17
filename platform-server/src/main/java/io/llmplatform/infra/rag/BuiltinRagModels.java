package io.llmplatform.infra.rag;

import io.llmplatform.common.UserDataPaths;
import io.llmplatform.infra.llamafile.LlamafileBinaryLocator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 内置 BGE-M3 / reranker 权重路径：用户数据目录优先，安装包 runtime 与开发机 .me/model 可复制进去。 权重文件不进 Git。 */
@Component
public class BuiltinRagModels {

    public static final String EMBEDDING_FILE = "bge-m3-q4_k_m.gguf";
    public static final String RERANK_FILE = "bge-reranker-v2-m3-q4_k_m.gguf";
    public static final int EMBEDDING_DIMENSION = 1024;

    private final UserDataPaths paths;
    private final LlamafileBinaryLocator binaryLocator;

    public BuiltinRagModels(UserDataPaths paths, LlamafileBinaryLocator binaryLocator) {
        this.paths = paths;
        this.binaryLocator = binaryLocator;
    }

    public Path embeddingModel() {
        return ensure(EMBEDDING_FILE, paths.embeddingModels(), "embedding");
    }

    public Path rerankModel() {
        return ensure(RERANK_FILE, paths.rerankModels(), "rerank");
    }

    public boolean embeddingPresent() {
        return Files.isRegularFile(paths.embeddingModels().resolve(EMBEDDING_FILE))
                || findSource(EMBEDDING_FILE, "embedding") != null;
    }

    public boolean rerankPresent() {
        return Files.isRegularFile(paths.rerankModels().resolve(RERANK_FILE))
                || findSource(RERANK_FILE, "rerank") != null;
    }

    private Path ensure(String fileName, Path targetDir, String kind) {
        Path target = targetDir.resolve(fileName);
        if (Files.isRegularFile(target)) {
            return target;
        }
        Path source = findSource(fileName, kind);
        if (source == null) {
            return null;
        }
        try {
            Files.createDirectories(targetDir);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            return Files.isRegularFile(source) ? source : null;
        }
    }

    private Path findSource(String fileName, String kind) {
        for (Path candidate : candidates(fileName, kind)) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private List<Path> candidates(String fileName, String kind) {
        Set<Path> pathsSet = new LinkedHashSet<>();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        pathsSet.add(cwd.resolve("runtime").resolve("models").resolve(kind).resolve(fileName));
        Path jarDir = binaryLocator.locateJarDirectory();
        if (jarDir != null) {
            pathsSet.add(
                    jarDir.resolve("runtime").resolve("models").resolve(kind).resolve(fileName));
            Path parent = jarDir.getParent();
            if (parent != null) {
                pathsSet.add(
                        parent.resolve("runtime")
                                .resolve("models")
                                .resolve(kind)
                                .resolve(fileName));
            }
        }
        Path walk = cwd;
        for (int i = 0; i < 8 && walk != null; i++) {
            pathsSet.add(walk.resolve(".me").resolve("model").resolve(fileName));
            walk = walk.getParent();
        }
        return new ArrayList<>(pathsSet);
    }
}
