package io.llmplatform.common.error;

import java.util.Map;

/** 平台领域错误，携带稳定错误码与文案键。 */
public class PlatformException extends RuntimeException {

    private final String code;
    private final String messageKey;
    private final Map<String, Object> params;

    public PlatformException(String code, String messageKey) {
        this(code, messageKey, Map.of());
    }

    public PlatformException(String code, String messageKey, Map<String, Object> params) {
        super(messageKey);
        this.code = code;
        this.messageKey = messageKey;
        this.params = params;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
