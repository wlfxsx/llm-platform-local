package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 本地模型文件行。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("models")
public class ModelEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String filePath;

    private Long importedAt;
}
