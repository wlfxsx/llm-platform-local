package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.ModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/** 每模型配置单表 CRUD，model_id 同时作为模型外键和唯一配置键。 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity> {}
