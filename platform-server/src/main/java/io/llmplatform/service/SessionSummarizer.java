package io.llmplatform.service;

import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.pojo.vo.ModelConfigView;
import java.util.List;

/** 把当前会话尚未摘要的旧消息压缩成一段摘要。 */
public interface SessionSummarizer {

    String summarize(String existingSummary, List<ChatMessage> oldMessages, ModelConfigView config);
}
