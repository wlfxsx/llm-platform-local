package io.llmplatform.infra.embedding;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** 本地占位嵌入，保证无外部模型时仍可做最小检索闭环。 */
@Component
public class LocalHashEmbeddingProvider implements EmbeddingProvider {

    @Override
    public String id() {
        return "local";
    }

    @Override
    public float[] embed(String text) {
        // 该向量只保证离线流程可运行，不表达真实语义；归一化仅用于让余弦分数保持稳定范围。
        float[] vector = new float[64];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            vector[i % vector.length] += bytes[i] / 128f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= (float) norm;
        }
        return vector;
    }
}
