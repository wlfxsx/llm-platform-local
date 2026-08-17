package io.llmplatform.infra.llamafile;

import org.springframework.stereotype.Component;

/** 保守估算：按码点计数，宁可高估占用以便更早触发压缩。 */
@Component
public class ConservativeTokenCounter implements TokenCounter {

    @Override
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.codePointCount(0, text.length()));
    }

    @Override
    public boolean accurate() {
        return false;
    }
}
