package io.llmplatform.repository;

import io.llmplatform.pojo.entity.McpServerRecord;
import io.llmplatform.repository.convert.EntityConverters;
import io.llmplatform.repository.entity.McpServerEntity;
import io.llmplatform.repository.mapper.McpServerMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MCP 服务配置的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class McpRepository {

    private final McpServerMapper mcpServerMapper;
    private final EntityConverters converters;

    public List<McpServerRecord> findAll() {
        return mcpServerMapper.selectList(null).stream().map(converters::toMcpRecord).toList();
    }

    public void insert(McpServerRecord server) {
        mcpServerMapper.insert(converters.toMcpEntity(server));
    }

    /** 返回受影响行数，调用方据此判断记录是否存在。 */
    public int updateEnabled(String id, boolean enabled) {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(id);
        entity.setEnabled(enabled);
        return mcpServerMapper.updateById(entity);
    }

    public void deleteById(String id) {
        mcpServerMapper.deleteById(id);
    }
}
