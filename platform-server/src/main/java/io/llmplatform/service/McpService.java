package io.llmplatform.service;

import io.llmplatform.common.constant.CapabilityIds;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.entity.McpServerRecord;
import io.llmplatform.repository.McpRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** MCP 客户端宿主骨架，连接细节由 AgentScope 适配层完成。 */
@Service
public class McpService {

    private final McpRepository mcpRepository;
    private final CapabilityService capabilities;

    public McpService(McpRepository mcpRepository, CapabilityService capabilities) {
        this.mcpRepository = mcpRepository;
        this.capabilities = capabilities;
    }

    public List<McpServerRecord> list() {
        return mcpRepository.findAll();
    }

    public McpServerRecord add(
            String name, String transport, String commandOrUrl, String configJson) {
        if ("streamable-http".equals(transport) || "sse".equals(transport)) {
            capabilities.require(CapabilityIds.NETWORK);
        }
        McpServerRecord server =
                new McpServerRecord(
                        UUID.randomUUID().toString(),
                        name,
                        transport,
                        commandOrUrl,
                        false,
                        configJson == null ? "{}" : configJson);
        mcpRepository.insert(server);
        return server;
    }

    public void setEnabled(String id, boolean enabled) {
        if (enabled) {
            capabilities.require(CapabilityIds.MCP);
        }
        if (mcpRepository.updateEnabled(id, enabled) == 0) {
            throw new PlatformException("NOT_FOUND", "error.notFound");
        }
    }

    public void delete(String id) {
        mcpRepository.deleteById(id);
    }
}
