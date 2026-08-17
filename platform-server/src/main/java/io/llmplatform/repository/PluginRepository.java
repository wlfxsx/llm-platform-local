package io.llmplatform.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.llmplatform.pojo.entity.PluginRecord;
import io.llmplatform.repository.convert.EntityConverters;
import io.llmplatform.repository.entity.PluginEntity;
import io.llmplatform.repository.mapper.PluginCapabilityMapper;
import io.llmplatform.repository.mapper.PluginMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 已安装插件的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class PluginRepository {

    private final PluginMapper pluginMapper;
    private final PluginCapabilityMapper pluginCapabilityMapper;
    private final EntityConverters converters;

    public List<StoredPlugin> findAll() {
        return pluginMapper
                .selectList(
                        Wrappers.<PluginEntity>lambdaQuery()
                                .orderByDesc(PluginEntity::getInstalledAt))
                .stream()
                .map(converters::toStoredPlugin)
                .toList();
    }

    public Optional<StoredPlugin> findById(String id) {
        return Optional.ofNullable(pluginMapper.selectById(id)).map(converters::toStoredPlugin);
    }

    public boolean exists(String id) {
        return pluginMapper.selectById(id) != null;
    }

    public void insert(StoredPlugin plugin, long installedAt) {
        PluginEntity entity = converters.toPluginEntity(plugin);
        entity.setInstalledAt(installedAt);
        pluginMapper.insert(entity);
    }

    public void updateEnabled(String id, boolean enabled) {
        PluginEntity entity = new PluginEntity();
        entity.setId(id);
        entity.setEnabled(enabled);
        pluginMapper.updateById(entity);
    }

    @Transactional
    public void deleteById(String id) {
        pluginCapabilityMapper.deleteByPluginId(id);
        pluginMapper.deleteById(id);
    }

    /** 插件表行，含原始清单 JSON。 */
    public record StoredPlugin(
            String id,
            String name,
            String version,
            boolean enabled,
            String directory,
            String manifestJson) {

        public PluginRecord toRecord(String status) {
            return new PluginRecord(id, name, version, enabled, directory, status);
        }
    }
}
