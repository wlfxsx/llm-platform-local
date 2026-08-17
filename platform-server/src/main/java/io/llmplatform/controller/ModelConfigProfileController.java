package io.llmplatform.controller;

import io.llmplatform.pojo.dto.ModelConfigProfileWriteRequest;
import io.llmplatform.pojo.vo.ModelConfigProfileView;
import io.llmplatform.service.ModelConfigProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 可命名模型参数策略。 */
@RestController
@RequestMapping("/api/model-config-profiles")
public class ModelConfigProfileController {

    private final ModelConfigProfileService service;

    public ModelConfigProfileController(ModelConfigProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<ModelConfigProfileView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ModelConfigProfileView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    public ModelConfigProfileView create(@Valid @RequestBody ModelConfigProfileWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public ModelConfigProfileView update(
            @PathVariable String id, @Valid @RequestBody ModelConfigProfileWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
