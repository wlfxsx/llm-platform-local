package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 对话会话行。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("sessions")
public class SessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String title;

    private Long createdAt;

    private Long updatedAt;

    private Integer nextSequence;
}
