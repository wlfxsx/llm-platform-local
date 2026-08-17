package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 当前会话的摘要水位，只能通过 session_id 访问。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("session_contexts")
public class SessionContextEntity {

    @TableId(type = IdType.INPUT)
    private String sessionId;

    private String summary;

    private Integer summarizedThroughSequence;

    private Integer summaryTokenCount;

    private Long updatedAt;
}
