package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 插件能力开关行，复合主键由 XML upsert 维护。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("plugin_capabilities")
public class PluginCapabilityEntity {

    @TableId(value = "plugin_id", type = IdType.INPUT)
    private String pluginId;

    private String capabilityId;

    private Boolean enabled;
}
