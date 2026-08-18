package io.llmplatform.service;

import io.llmplatform.common.UserDataPaths;
import io.llmplatform.common.constant.CapabilityIds;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.entity.SkillRecord;
import io.llmplatform.repository.SkillRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** 加载 SKILL.md 技能包。 */
@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final UserDataPaths paths;
    private final CapabilityService capabilities;

    public SkillService(
            SkillRepository skillRepository, UserDataPaths paths, CapabilityService capabilities) {
        this.skillRepository = skillRepository;
        this.paths = paths;
        this.capabilities = capabilities;
    }

    public List<SkillRecord> list() {
        return skillRepository.findAll();
    }

    public SkillRecord importSkill(Path sourceDir) {
        paths.ensureDirectories();
        Path skillFile = sourceDir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
        String id = UUID.randomUUID().toString();
        Path target = paths.skills().resolve(id);
        try {
            Files.createDirectories(target);
            Files.copy(skillFile, target.resolve("SKILL.md"));
        } catch (IOException ex) {
            throw new PlatformException("FILE_COPY_FAILED", "error.fileCopyFailed");
        }
        SkillRecord skill =
                new SkillRecord(id, sourceDir.getFileName().toString(), target.toString(), false);
        skillRepository.insert(skill);
        return skill;
    }

    public void setEnabled(String id, boolean enabled) {
        if (enabled) {
            capabilities.require(CapabilityIds.SKILLS);
        }
        if (skillRepository.updateEnabled(id, enabled) == 0) {
            throw new PlatformException("NOT_FOUND", "error.notFound");
        }
    }

    public void delete(String id) {
        SkillRecord skill =
                skillRepository
                        .findById(id)
                        .orElseThrow(() -> new PlatformException("NOT_FOUND", "error.notFound"));
        // 先删数据库记录，避免目录残留导致列表仍显示已删除技能。
        skillRepository.deleteById(id);
        try {
            Path dir = Path.of(skill.directory());
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(
                                    path -> {
                                        try {
                                            Files.deleteIfExists(path);
                                        } catch (IOException ignored) {
                                            // 尽力删除技能目录。
                                        }
                                    });
                }
            }
        } catch (IOException ignored) {
            // 目录删除失败不回滚数据库，避免已卸载技能再次出现在列表中。
        }
    }

    /** 把已启用技能拼进系统提示；单个文件损坏只跳过该项，避免一个技能阻断整次对话。 */
    public String promptFor(String sessionId) {
        if (!capabilities.isEnabledForSession(sessionId, CapabilityIds.SKILLS)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (SkillRecord skill : skillRepository.findAll()) {
            if (!skill.enabled()) {
                continue;
            }
            Path file = Path.of(skill.directory()).resolve("SKILL.md");
            if (Files.isRegularFile(file)) {
                try (Stream<String> lines = Files.lines(file)) {
                    builder.append(String.join("\n", lines.toList())).append('\n');
                } catch (IOException ignored) {
                    // 单个技能读取失败不影响其它技能。
                }
            }
        }
        return builder.toString();
    }
}
