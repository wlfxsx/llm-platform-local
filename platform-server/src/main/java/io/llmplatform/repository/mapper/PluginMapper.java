package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.PluginEntity;
import org.apache.ibatis.annotations.Mapper;

/** 插件主表 CRUD；能力关联表由聚合 Repository 在同一事务中维护。 */
@Mapper
public interface PluginMapper extends BaseMapper<PluginEntity> {}
