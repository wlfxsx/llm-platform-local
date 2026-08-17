package io.llmplatform.controller;

import io.llmplatform.pojo.dto.ModelConfigUpdateRequest;
import io.llmplatform.pojo.dto.PathImportRequest;
import io.llmplatform.pojo.entity.ModelRecord;
import io.llmplatform.pojo.vo.ModelConfigView;
import io.llmplatform.service.ModelConfigService;
import io.llmplatform.service.ModelService;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本地模型导入、选择与每模型参数。 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelService modelService;
    private final ModelConfigService modelConfigService;

    public ModelController(ModelService modelService, ModelConfigService modelConfigService) {
        this.modelService = modelService;
        this.modelConfigService = modelConfigService;
    }

    @GetMapping
    public List<ModelRecord> list() {
        return modelService.list();
    }

    @PostMapping("/import")
    public ModelRecord importModel(@Valid @RequestBody PathImportRequest body) {
        return modelService.importFile(Path.of(body.path()));
    }

    @PostMapping("/{id}/select")
    public void select(@PathVariable String id) {
        modelService.select(id);
    }

    @GetMapping("/{id}/config")
    public ModelConfigView getConfig(@PathVariable String id) {
        return modelConfigService.get(id);
    }

    @PutMapping("/{id}/config")
    public ModelConfigView putConfig(
            @PathVariable String id, @Valid @RequestBody ModelConfigUpdateRequest body) {
        return modelConfigService.save(id, body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        modelService.delete(id);
    }
}
