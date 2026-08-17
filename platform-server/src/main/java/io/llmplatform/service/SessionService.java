package io.llmplatform.service;

import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.pojo.vo.SessionMessageView;
import io.llmplatform.pojo.vo.SessionView;
import io.llmplatform.repository.ChatRepository;
import io.llmplatform.repository.entity.MessageEntity;
import io.llmplatform.repository.entity.SessionEntity;
import java.util.List;
import org.springframework.stereotype.Service;

/** 会话的查询、撤回与删除；会话的创建与标题由 ChatService 在首条消息时完成。 */
@Service
public class SessionService {

    /** 侧栏一行放不下整句输入，标题按首行截断到可读长度。 */
    private static final int MAX_TITLE_LENGTH = 40;

    private final ChatRepository chatRepository;
    private final CapabilityService capabilityService;

    public SessionService(ChatRepository chatRepository, CapabilityService capabilityService) {
        this.chatRepository = chatRepository;
        this.capabilityService = capabilityService;
    }

    public List<SessionView> list() {
        return chatRepository.findSessions().stream().map(SessionService::toView).toList();
    }

    public SessionView get(String id) {
        return toView(require(id));
    }

    public List<SessionMessageView> messages(String id) {
        require(id);
        return chatRepository.findMessages(id).stream().map(SessionService::toView).toList();
    }

    /** 撤回或修改：删除该序号及之后的消息，返回剩余历史。 */
    public List<SessionMessageView> truncate(String id, int sequenceNo) {
        require(id);
        chatRepository.deleteMessagesFrom(id, sequenceNo, System.currentTimeMillis());
        return messages(id);
    }

    public void delete(String id) {
        require(id);
        chatRepository.deleteSession(id);
        capabilityService.clearSession(id);
    }

    /** 新会话的标题取首条用户消息，避免侧栏出现一串无法区分的“会话”。 */
    public static String titleFrom(List<ChatMessage> messages) {
        if (messages == null) {
            return "";
        }
        for (ChatMessage message : messages) {
            if ("user".equals(message.role())) {
                return normalizeTitle(message.content());
            }
        }
        return "";
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String single = title.replaceAll("\\s+", " ").trim();
        return single.length() <= MAX_TITLE_LENGTH
                ? single
                : single.substring(0, MAX_TITLE_LENGTH) + "…";
    }

    private SessionEntity require(String id) {
        return chatRepository
                .findSession(id)
                .orElseThrow(() -> new PlatformException("NOT_FOUND", "error.notFound"));
    }

    private static SessionView toView(SessionEntity entity) {
        return new SessionView(
                entity.getId(),
                entity.getTitle() == null ? "" : entity.getTitle(),
                entity.getCreatedAt() == null ? 0 : entity.getCreatedAt(),
                entity.getUpdatedAt() == null ? 0 : entity.getUpdatedAt());
    }

    private static SessionMessageView toView(MessageEntity entity) {
        return new SessionMessageView(
                entity.getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getCreatedAt() == null ? 0 : entity.getCreatedAt(),
                entity.getSequenceNo() == null ? 0 : entity.getSequenceNo());
    }
}
