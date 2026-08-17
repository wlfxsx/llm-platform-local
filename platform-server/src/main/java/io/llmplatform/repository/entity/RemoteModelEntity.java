package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** OpenAI 兼容远程模型配置；密钥只存 secret_ref。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("remote_models")
public class RemoteModelEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String nameNormalized;

    private String baseUrl;

    private String modelName;

    private String secretRef;

    private Long createdAt;

    private Long updatedAt;
}
