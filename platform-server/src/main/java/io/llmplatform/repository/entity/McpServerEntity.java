package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** MCP 服务配置行。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("mcp_servers")
public class McpServerEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String transport;

    private String commandOrUrl;

    private Boolean enabled;

    private String configJson;
}
