package io.llmplatform.controller;

import io.llmplatform.pojo.dto.MessageTruncateRequest;
import io.llmplatform.pojo.vo.SessionMessageView;
import io.llmplatform.pojo.vo.SessionView;
import io.llmplatform.service.SessionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 会话列表与历史消息；对话提交仍在 /api/ai/chat。 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @GetMapping
    public List<SessionView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public SessionView get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/{id}/messages")
    public List<SessionMessageView> messages(@PathVariable String id) {
        return service.messages(id);
    }

    @PostMapping("/{id}/messages/truncate")
    public List<SessionMessageView> truncate(
            @PathVariable String id, @Valid @RequestBody MessageTruncateRequest body) {
        return service.truncate(id, body.sequenceNo());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
