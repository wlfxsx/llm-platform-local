package io.llmplatform.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.repository.entity.MessageEntity;
import io.llmplatform.repository.entity.SessionContextEntity;
import io.llmplatform.repository.entity.SessionEntity;
import io.llmplatform.repository.mapper.MessageMapper;
import io.llmplatform.repository.mapper.SessionContextMapper;
import io.llmplatform.repository.mapper.SessionMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 会话、消息与当前会话摘要的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class ChatRepository {

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final SessionContextMapper sessionContextMapper;

    public void insertSession(String id, String title, long now) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setTitle(title);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setNextSequence(1);
        sessionMapper.insert(session);
    }

    /** 三参入口也必须走代理事务：本类自调用四参方法时 {@code @Transactional} 不会生效。 */
    @Transactional
    public void insertMessage(String sessionId, ChatMessage message, long createdAt) {
        insertMessage(sessionId, message, createdAt, 0);
    }

    /** 最近活跃的会话排在最前，与客户端侧栏的展示顺序一致；空会话不返回。 */
    public List<SessionEntity> findSessions() {
        return sessionMapper.selectWithMessages();
    }

    public Optional<SessionEntity> findSession(String sessionId) {
        return Optional.ofNullable(sessionMapper.selectById(sessionId));
    }

    /** 读取整段历史用于界面回放；上下文构建仍走限量的 findRecentMessages。 */
    public List<MessageEntity> findMessages(String sessionId) {
        return messageMapper.selectList(
                Wrappers.<MessageEntity>lambdaQuery()
                        .eq(MessageEntity::getSessionId, sessionId)
                        .orderByAsc(MessageEntity::getCreatedAt)
                        .orderByAsc(MessageEntity::getSequenceNo));
    }

    public void updateTitle(String sessionId, String title, long updatedAt) {
        SessionEntity entity = new SessionEntity();
        entity.setId(sessionId);
        entity.setTitle(title);
        entity.setUpdatedAt(updatedAt);
        sessionMapper.updateById(entity);
    }

    /**
     * 删除该序号及其之后的所有消息，并把序号水位退回，供撤回与修改后重发使用。
     *
     * <p>摘要一旦覆盖到被删区间就不再可信，必须连同摘要行一起清理，否则模型会读到已撤回的内容。
     */
    @Transactional
    public void deleteMessagesFrom(String sessionId, int sequenceNo, long updatedAt) {
        messageMapper.delete(
                Wrappers.<MessageEntity>lambdaQuery()
                        .eq(MessageEntity::getSessionId, sessionId)
                        .ge(MessageEntity::getSequenceNo, sequenceNo));
        SessionContextEntity context = sessionContextMapper.selectById(sessionId);
        if (context != null
                && context.getSummarizedThroughSequence() != null
                && context.getSummarizedThroughSequence() >= sequenceNo) {
            sessionContextMapper.deleteById(sessionId);
        }
        SessionEntity session = new SessionEntity();
        session.setId(sessionId);
        session.setNextSequence(sequenceNo);
        session.setUpdatedAt(updatedAt);
        sessionMapper.updateById(session);
    }

    /** 消息、摘要与会话行必须一起删除，否则残留历史会在下次同 ID 会话里复现。 */
    @Transactional
    public void deleteSession(String sessionId) {
        messageMapper.delete(
                Wrappers.<MessageEntity>lambdaQuery().eq(MessageEntity::getSessionId, sessionId));
        sessionContextMapper.deleteById(sessionId);
        sessionMapper.deleteById(sessionId);
    }

    /** 序号分配、会话更新时间和消息插入必须处于同一事务，避免并发或失败后出现水位跳跃与孤立消息。 */
    @Transactional
    public void insertMessage(
            String sessionId, ChatMessage message, long createdAt, int tokenCount) {
        sessionMapper.incrementSequence(sessionId, createdAt);
        SessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new PlatformException("NOT_FOUND", "error.notFound");
        }
        int sequenceNo = session.getNextSequence() - 1;
        MessageEntity entity = new MessageEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setSessionId(sessionId);
        entity.setRole(message.role());
        entity.setContent(message.content());
        entity.setCreatedAt(createdAt);
        entity.setSequenceNo(sequenceNo);
        entity.setTokenCount(tokenCount);
        messageMapper.insert(entity);
    }

    /** 只读取指定会话最近的消息，并恢复为正序，避免任何跨会话上下文混入。 */
    public List<ChatMessage> findRecentMessages(String sessionId, int limit) {
        return messageMapper.selectRecentMessages(sessionId, limit);
    }

    public List<MessageEntity> findMessagesAfter(String sessionId, int summarizedThroughSequence) {
        return messageMapper.selectList(
                Wrappers.<MessageEntity>lambdaQuery()
                        .eq(MessageEntity::getSessionId, sessionId)
                        .gt(MessageEntity::getSequenceNo, summarizedThroughSequence)
                        .orderByAsc(MessageEntity::getSequenceNo));
    }

    public Optional<SessionContextEntity> findContext(String sessionId) {
        return Optional.ofNullable(sessionContextMapper.selectById(sessionId));
    }

    /** 同时推进摘要正文、序号水位和 Token 计数；任一字段失败都不应留下部分可见的摘要状态。 */
    @Transactional
    public void upsertContext(
            String sessionId,
            String summary,
            int summarizedThroughSequence,
            int summaryTokenCount,
            long updatedAt) {
        SessionContextEntity existing = sessionContextMapper.selectById(sessionId);
        SessionContextEntity entity = existing == null ? new SessionContextEntity() : existing;
        entity.setSessionId(sessionId);
        entity.setSummary(summary);
        entity.setSummarizedThroughSequence(summarizedThroughSequence);
        entity.setSummaryTokenCount(summaryTokenCount);
        entity.setUpdatedAt(updatedAt);
        if (existing == null) {
            sessionContextMapper.insert(entity);
        } else {
            sessionContextMapper.updateById(entity);
        }
    }

    public void updateTokenCount(String messageId, int tokenCount) {
        MessageEntity entity = new MessageEntity();
        entity.setId(messageId);
        entity.setTokenCount(tokenCount);
        messageMapper.updateById(entity);
    }
}
