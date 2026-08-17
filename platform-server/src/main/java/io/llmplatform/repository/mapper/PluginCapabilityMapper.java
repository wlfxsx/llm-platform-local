package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.PluginCapabilityEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 插件能力声明 Mapper，复合键操作和按插件清理使用自定义 SQL。 */
@Mapper
public interface PluginCapabilityMapper extends BaseMapper<PluginCapabilityEntity> {

    /** 查询插件对单项能力的覆盖值，空值表示未声明覆盖。 */
    Boolean selectEnabled(
            @Param("pluginId") String pluginId, @Param("capabilityId") String capabilityId);

    /** 原子写入或替换插件能力覆盖值。 */
    int upsertEnabled(
            @Param("pluginId") String pluginId,
            @Param("capabilityId") String capabilityId,
            @Param("enabled") int enabled);

    /** 删除插件前清理全部能力关联，避免残留不可达记录。 */
    int deleteByPluginId(@Param("pluginId") String pluginId);
}
