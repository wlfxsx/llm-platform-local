package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.SessionContextEntity;
import org.apache.ibatis.annotations.Mapper;

/** 会话摘要 CRUD，以 session_id 强制保持一会话一份摘要状态。 */
@Mapper
public interface SessionContextMapper extends BaseMapper<SessionContextEntity> {}
