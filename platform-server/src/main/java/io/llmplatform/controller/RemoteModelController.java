package io.llmplatform.controller;

import io.llmplatform.pojo.dto.RemoteModelWriteRequest;
import io.llmplatform.pojo.vo.RemoteModelTestResult;
import io.llmplatform.pojo.vo.RemoteModelView;
import io.llmplatform.service.RemoteModelService;
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

/** 远程 OpenAI 兼容模型配置。 */
@RestController
@RequestMapping("/api/remote-models")
public class RemoteModelController {

    private final RemoteModelService service;

    public RemoteModelController(RemoteModelService service) {
        this.service = service;
    }

    @GetMapping
    public List<RemoteModelView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RemoteModelView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    public RemoteModelView create(@Valid @RequestBody RemoteModelWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public RemoteModelView update(
            @PathVariable String id, @Valid @RequestBody RemoteModelWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @PostMapping("/{id}/test")
    public RemoteModelTestResult test(@PathVariable String id) {
        return service.test(id);
    }
}
