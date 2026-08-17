package io.llmplatform.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 非流式或流式对话请求。 */
public record ChatRequest(
        String sessionId, String pluginId, @NotEmpty List<@Valid ChatMessage> messages) {}
