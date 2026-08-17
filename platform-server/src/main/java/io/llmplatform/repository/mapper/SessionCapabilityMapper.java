package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.SessionCapabilityEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 会话能力覆盖 Mapper，复合键 upsert 使用 SQLite 方言 XML。 */
@Mapper
public interface SessionCapabilityMapper extends BaseMapper<SessionCapabilityEntity> {

    /** 查询指定会话对单项能力的覆盖值，空值表示继承全局设置。 */
    Boolean selectEnabled(
            @Param("sessionId") String sessionId, @Param("capabilityId") String capabilityId);

    /** 原子写入或替换指定会话的能力覆盖值。 */
    int upsertEnabled(
            @Param("sessionId") String sessionId,
            @Param("capabilityId") String capabilityId,
            @Param("enabled") int enabled);
}
