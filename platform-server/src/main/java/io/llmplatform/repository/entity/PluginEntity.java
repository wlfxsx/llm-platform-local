package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 已安装插件行。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("plugins")
public class PluginEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String version;

    private Boolean enabled;

    private String directory;

    private String manifestJson;

    private Long installedAt;
}
