package io.llmplatform.pojo.vo;

import java.util.Map;

/** 统一错误响应。message 由当前应用语言解析。 */
public record ApiError(
        String code, String messageKey, Map<String, Object> params, String message) {}
