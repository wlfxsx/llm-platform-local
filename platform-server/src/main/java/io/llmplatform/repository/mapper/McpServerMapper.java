package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.McpServerEntity;
import org.apache.ibatis.annotations.Mapper;

/** MCP 服务配置单表 CRUD，连接生命周期不在持久化层处理。 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServerEntity> {}
