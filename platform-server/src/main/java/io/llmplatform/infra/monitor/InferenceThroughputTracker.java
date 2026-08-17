package io.llmplatform.infra.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 在平台侧直接统计生成吞吐。
 *
 * <p>llamafile 各版本的 /metrics 指标名并不统一，甚至可能没有该端点； 而流式响应每个增量基本对应一个 token，据此计算的 tok/s
 * 与推理端读数同量级，可保证监控页始终有数据。
 */
@Component
public class InferenceThroughputTracker {

    /** 生成结束后保留最后一次读数的时长，超过则认为数据已过期。 */
    private static final long RETAIN_MILLIS = 30_000;

    private final AtomicInteger activeGenerations = new AtomicInteger();
    private final AtomicReference<Double> lastTokensPerSecond = new AtomicReference<>();
    private final AtomicLong lastFinishedAt = new AtomicLong();
    private final AtomicReference<Generation> current = new AtomicReference<>();

    public Generation begin() {
        Generation generation = new Generation();
        current.set(generation);
        activeGenerations.incrementAndGet();
        return generation;
    }

    public int activeGenerations() {
        return activeGenerations.get();
    }

    /** 生成中返回实时吞吐，生成结束后短时间内返回最后一次读数，再之后返回 null。 */
    public Double tokensPerSecond() {
        Generation generation = current.get();
        if (generation != null && !generation.finished) {
            Double live = generation.tokensPerSecond();
            if (live != null) {
                return live;
            }
        }
        return System.currentTimeMillis() - lastFinishedAt.get() <= RETAIN_MILLIS
                ? lastTokensPerSecond.get()
                : null;
    }

    /** 单次生成的计数器；用 try-with-resources 保证异常路径也会归还活跃计数。 */
    public final class Generation implements AutoCloseable {

        private final long startedAt = System.nanoTime();
        private final AtomicLong tokens = new AtomicLong();
        private volatile boolean finished;

        private Generation() {}

        public void onDelta() {
            tokens.incrementAndGet();
        }

        @Override
        public void close() {
            if (finished) {
                return;
            }
            finished = true;
            Double rate = tokensPerSecond();
            if (rate != null) {
                lastTokensPerSecond.set(rate);
                lastFinishedAt.set(System.currentTimeMillis());
            }
            activeGenerations.decrementAndGet();
        }

        private Double tokensPerSecond() {
            long count = tokens.get();
            double seconds = (System.nanoTime() - startedAt) / 1_000_000_000d;
            // 刚开始生成时样本太少，给出的数值会剧烈跳动，先不上报。
            return count < 2 || seconds <= 0.05 ? null : count / seconds;
        }
    }
}
