package io.llmplatform.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.llmplatform.repository.entity.SessionCapabilityEntity;
import io.llmplatform.repository.mapper.CapabilityMapper;
import io.llmplatform.repository.mapper.PluginCapabilityMapper;
import io.llmplatform.repository.mapper.SessionCapabilityMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 全局、会话和插件三级能力开关的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class CapabilityRepository {

    private final CapabilityMapper capabilityMapper;
    private final SessionCapabilityMapper sessionCapabilityMapper;
    private final PluginCapabilityMapper pluginCapabilityMapper;

    public Optional<Boolean> findGlobal(String capabilityId) {
        return Optional.ofNullable(capabilityMapper.selectEnabled(capabilityId));
    }

    public Optional<Boolean> findSession(String sessionId, String capabilityId) {
        return Optional.ofNullable(sessionCapabilityMapper.selectEnabled(sessionId, capabilityId));
    }

    public Optional<Boolean> findPlugin(String pluginId, String capabilityId) {
        return Optional.ofNullable(pluginCapabilityMapper.selectEnabled(pluginId, capabilityId));
    }

    public void upsertGlobal(String capabilityId, boolean enabled) {
        capabilityMapper.upsertEnabled(capabilityId, enabled ? 1 : 0);
    }

    public void upsertSession(String sessionId, String capabilityId, boolean enabled) {
        sessionCapabilityMapper.upsertEnabled(sessionId, capabilityId, enabled ? 1 : 0);
    }

    public void upsertPlugin(String pluginId, String capabilityId, boolean enabled) {
        pluginCapabilityMapper.upsertEnabled(pluginId, capabilityId, enabled ? 1 : 0);
    }

    public void insertDefault(String capabilityId, boolean enabled) {
        capabilityMapper.insertDefault(capabilityId, enabled ? 1 : 0);
    }

    /** 会话删除后清掉其能力覆盖，避免同 ID 复用时读到旧开关。 */
    public void deleteSession(String sessionId) {
        sessionCapabilityMapper.delete(
                Wrappers.<SessionCapabilityEntity>lambdaQuery()
                        .eq(SessionCapabilityEntity::getSessionId, sessionId));
    }
}
