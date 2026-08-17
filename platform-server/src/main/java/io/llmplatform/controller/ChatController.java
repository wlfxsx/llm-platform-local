package io.llmplatform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.common.i18n.MessageCatalog;
import io.llmplatform.pojo.dto.ChatRequest;
import io.llmplatform.pojo.vo.ChatResponse;
import io.llmplatform.service.ChatService;
import io.llmplatform.service.SettingsService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 非流式 REST 与 SSE 流式对话。 */
@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final MessageCatalog messages;
    private final SettingsService settingsService;

    public ChatController(
            ChatService chatService,
            ObjectMapper objectMapper,
            MessageCatalog messages,
            SettingsService settingsService) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.settingsService = settingsService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.complete(request);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        String requestId = UUID.randomUUID().toString();
        Thread.startVirtualThread(
                () -> {
                    try {
                        String sessionId =
                                chatService.stream(
                                        request,
                                        requestId,
                                        delta ->
                                                send(
                                                        emitter,
                                                        requestId,
                                                        "delta",
                                                        Map.of("text", delta)),
                                        (type, payload) -> send(emitter, requestId, type, payload));
                        send(emitter, requestId, "done", Map.of("sessionId", sessionId));
                        emitter.complete();
                    } catch (Exception ex) {
                        send(emitter, requestId, "error", errorPayload(ex));
                        emitter.completeWithError(ex);
                    }
                });
        return emitter;
    }

    /** 把领域错误的 messageKey 与已本地化文案一并下发，避免桌面端只能显示笼统失败提示。 */
    private Map<String, Object> errorPayload(Exception ex) {
        String messageKey = "error.internal";
        Map<String, Object> params = Map.of();
        if (ex instanceof PlatformException platform) {
            messageKey = platform.getMessageKey();
            params = platform.getParams();
        }
        String language = settingsService.current().language();
        String message = messages.get(messageKey, language, params);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageKey", messageKey);
        payload.put("message", message);
        return payload;
    }

    private void send(
            SseEmitter emitter, String requestId, String type, Map<String, Object> payload) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .data(
                                    objectMapper.writeValueAsString(
                                            Map.of(
                                                    "type",
                                                    type,
                                                    "requestId",
                                                    requestId,
                                                    "payload",
                                                    payload))));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
