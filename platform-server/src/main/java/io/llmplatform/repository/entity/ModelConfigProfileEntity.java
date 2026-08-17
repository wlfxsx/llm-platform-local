package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 用户命名的完整模型参数策略。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("model_config_profiles")
public class ModelConfigProfileEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String nameNormalized;

    private String paramsJson;

    private Long createdAt;

    private Long updatedAt;
}
