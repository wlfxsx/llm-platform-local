package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.SkillEntity;
import org.apache.ibatis.annotations.Mapper;

/** 技能元数据单表 CRUD；技能文件本体仍由服务层管理。 */
@Mapper
public interface SkillMapper extends BaseMapper<SkillEntity> {}
