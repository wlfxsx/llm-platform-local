package io.llmplatform.common.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import org.springframework.stereotype.Component;

/** 按应用语言解析用户可见文案。 */
@Component
public class MessageCatalog {

    public String get(String messageKey, String language) {
        return get(messageKey, language, Map.of());
    }

    public String get(String messageKey, String language, Map<String, Object> params) {
        Locale locale =
                "en".equalsIgnoreCase(language) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
        String pattern = bundle.containsKey(messageKey) ? bundle.getString(messageKey) : messageKey;
        if (params == null || params.isEmpty()) {
            return pattern;
        }
        Object[] args = params.values().toArray();
        return MessageFormat.format(pattern, args);
    }
}
