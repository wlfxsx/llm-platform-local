package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.pojo.dto.ChatMessage;
import io.llmplatform.repository.entity.MessageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 消息 CRUD，并承载 SQLite 最近消息双重排序查询。 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {

    /** 先倒序限量再正序返回，兼顾最近窗口语义与模型接收顺序。 */
    List<ChatMessage> selectRecentMessages(
            @Param("sessionId") String sessionId, @Param("limit") int limit);
}
