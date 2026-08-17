package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.ModelEntity;
import org.apache.ibatis.annotations.Mapper;

/** 模型单表 CRUD，排序和对外转换由 Repository 统一处理。 */
@Mapper
public interface ModelMapper extends BaseMapper<ModelEntity> {}
