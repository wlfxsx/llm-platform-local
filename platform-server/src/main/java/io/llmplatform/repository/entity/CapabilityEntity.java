package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 全局能力开关行。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("capabilities")
public class CapabilityEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private Boolean enabled;
}
