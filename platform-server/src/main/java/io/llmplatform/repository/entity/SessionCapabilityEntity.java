package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 会话级能力开关行，复合主键由 XML upsert 维护。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("session_capabilities")
public class SessionCapabilityEntity {

    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    private String capabilityId;

    private Boolean enabled;
}
