package io.llmplatform.pojo.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/** 插件清单。 */
public record PluginManifest(
        String id,
        String name,
        String version,
        String entry,
        Map<String, PluginCapabilityDecl> capabilities) {

    public static PluginManifest fromJson(JsonNode node) {
        Map<String, PluginCapabilityDecl> capabilities = new LinkedHashMap<>();
        JsonNode caps = node.path("capabilities");
        for (Map.Entry<String, JsonNode> entry : caps.properties()) {
            capabilities.put(
                    entry.getKey(),
                    new PluginCapabilityDecl(
                            entry.getValue().path("required").asBoolean(false),
                            entry.getValue().path("enabledByDefault").asBoolean(false)));
        }
        return new PluginManifest(
                node.path("id").asText(),
                node.path("name").asText(node.path("id").asText()),
                node.path("version").asText("0.0.0"),
                node.path("entry").asText("placeholder"),
                capabilities);
    }
}
