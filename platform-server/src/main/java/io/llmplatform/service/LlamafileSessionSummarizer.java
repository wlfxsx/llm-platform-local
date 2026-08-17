package io.llmplatform.service;

import io.llmplatform.infra.llm.ChatModelClient;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.pojo.vo.ModelConfigView;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 用当前推理后端的非流式接口生成会话摘要。 */
@Component
public class LlamafileSessionSummarizer implements SessionSummarizer {

    private final ChatModelClient chatClient;

    public LlamafileSessionSummarizer(ChatModelClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 把已有摘要作为前序状态与新淘汰消息合并，使用低温度降低事实漂移，并用独立上限避免摘要挤占正常回复预算。 */
    @Override
    public String summarize(
            String existingSummary, List<ChatMessage> oldMessages, ModelConfigView config) {
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(
                new ChatMessage(
                        "system",
                        "Summarize the conversation so far for later turns in this same session."
                                + " Keep facts, decisions, names and open tasks."
                                + " Do not invent content. Reply with the summary only."));
        if (existingSummary != null && !existingSummary.isBlank()) {
            prompt.add(new ChatMessage("system", "Previous summary:\n" + existingSummary));
        }
        prompt.addAll(oldMessages);
        prompt.add(new ChatMessage("user", "Write the updated session summary now."));
        double temperature = Math.min(0.2, config.temperature());
        return chatClient.completeNonStream(prompt, config, temperature, config.summaryMaxTokens());
    }
}
