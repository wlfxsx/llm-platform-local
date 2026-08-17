package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 会话消息行。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("messages")
public class MessageEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String sessionId;

    private String role;

    private String content;

    private Long createdAt;

    private Integer sequenceNo;

    private Integer tokenCount;
}
