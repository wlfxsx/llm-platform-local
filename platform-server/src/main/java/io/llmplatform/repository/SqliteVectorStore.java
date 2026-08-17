package io.llmplatform.repository;

import io.llmplatform.pojo.vo.VectorHit;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 默认暴力检索实现。Vec1 可用时由调用方替换，保证未加载扩展时应用仍能启动。 */
@Component
public class SqliteVectorStore implements VectorStore {

    private final JdbcTemplate jdbcTemplate;

    public SqliteVectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(String collection, String id, float[] embedding, String payload) {
        requireRag(collection);
        jdbcTemplate.update(
                "UPDATE rag_chunks SET embedding = ? WHERE id = ?", toBytes(embedding), id);
    }

    @Override
    public List<VectorHit> search(String collection, float[] query, int topK) {
        requireRag(collection);
        String sql = "SELECT id, content, embedding FROM rag_chunks WHERE embedding IS NOT NULL";
        List<VectorHit> hits = new ArrayList<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    float[] stored = fromBytes(rs.getBytes("embedding"));
                    if (stored.length == 0 || stored.length != query.length) {
                        return;
                    }
                    hits.add(
                            new VectorHit(
                                    rs.getString("id"),
                                    rs.getString("content"),
                                    cosine(query, stored)));
                });
        return hits.stream()
                .sorted(Comparator.comparingDouble(VectorHit::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public void delete(String collection, String id) {
        requireRag(collection);
        jdbcTemplate.update("UPDATE rag_chunks SET embedding = NULL WHERE id = ?", id);
    }

    @Override
    public void clear(String collection) {
        requireRag(collection);
        jdbcTemplate.update("UPDATE rag_chunks SET embedding = NULL");
    }

    private static void requireRag(String collection) {
        if (!"rag".equals(collection)) {
            throw new IllegalArgumentException("Unsupported vector collection: " + collection);
        }
    }

    private byte[] toBytes(float[] values) {
        // 固定小端序与 SQLite 向量扩展和既有本地数据格式保持一致，禁止使用平台默认字节序。
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private float[] fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return new float[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        // 非 4 字节整数倍的尾部不会形成完整 float，按完整元素数读取以兼容旧损坏数据。
        float[] values = new float[bytes.length / 4];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getFloat();
        }
        return values;
    }

    private double cosine(float[] left, float[] right) {
        // 维度不一致时只比较公共前缀，零向量返回中性分数，保证降级检索不会除零崩溃。
        int size = Math.min(left.length, right.length);
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < size; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
