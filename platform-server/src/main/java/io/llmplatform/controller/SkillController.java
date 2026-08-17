package io.llmplatform.controller;

import io.llmplatform.pojo.dto.PathImportRequest;
import io.llmplatform.pojo.entity.SkillRecord;
import io.llmplatform.service.SkillService;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 技能包导入与启停。 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public List<SkillRecord> list() {
        return skillService.list();
    }

    @PostMapping("/import")
    public SkillRecord importSkill(@Valid @RequestBody PathImportRequest body) {
        return skillService.importSkill(Path.of(body.path()));
    }

    @PostMapping("/{id}/enable")
    public void enable(@PathVariable String id) {
        skillService.setEnabled(id, true);
    }

    @PostMapping("/{id}/disable")
    public void disable(@PathVariable String id) {
        skillService.setEnabled(id, false);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        skillService.delete(id);
    }
}
