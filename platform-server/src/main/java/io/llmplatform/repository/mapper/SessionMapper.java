package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.SessionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 会话 CRUD，并以单条 SQL 原子推进消息序号和更新时间。 */
@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {

    /** 为下一条消息预留稳定序号，同时刷新会话活跃时间。 */
    int incrementSequence(@Param("id") String id, @Param("now") long now);

    /** 只返回已有消息的会话，按最近活跃排序；空会话不进入列表。 */
    List<SessionEntity> selectWithMessages();
}
