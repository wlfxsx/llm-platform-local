package io.llmplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.error.PlatformException;
import java.util.Iterator;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 校验高级推理 JSON：必须是对象，且不能覆盖对话请求的保留键。 */
@Component
public class AdvancedInferenceParamsValidator {

    /** 限制扩展 JSON 体积，避免把过大对象合并进每次推理请求。 */
    static final int MAX_BYTES = 8192;

    /** 这些键由平台协议固定，不能由高级参数改写对话主体或流式语义。 */
    private static final Set<String> RESERVED =
            Set.of("model", "messages", "stream", "prompt", "input");

    private final ObjectMapper objectMapper;

    public AdvancedInferenceParamsValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 将空值归一化为空对象，并限制结构、序列化体积、保留键和单字段容器规模，防止扩展参数改变协议语义或制造过大请求。 */
    public JsonNode normalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!node.isObject()) {
            throw new PlatformException("INVALID_REQUEST", "error.advancedParamsInvalid");
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(node);
            if (bytes.length > MAX_BYTES) {
                throw new PlatformException("INVALID_REQUEST", "error.advancedParamsInvalid");
            }
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException("INVALID_REQUEST", "error.advancedParamsInvalid");
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (RESERVED.contains(name) || name.isBlank()) {
                throw new PlatformException("INVALID_REQUEST", "error.advancedParamsInvalid");
            }
            JsonNode value = node.get(name);
            // 数组/对象字段限制规模，避免 stop 列表或嵌套对象膨胀成异常大请求。
            if (value != null && (value.isPojo() || value.size() > 256)) {
                throw new PlatformException("INVALID_REQUEST", "error.advancedParamsInvalid");
            }
        }
        return node;
    }
}
