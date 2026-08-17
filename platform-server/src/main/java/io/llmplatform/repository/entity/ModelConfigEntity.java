package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 单个模型的运行、采样与压缩参数。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("model_configs")
public class ModelConfigEntity {

    @TableId(type = IdType.INPUT)
    private String modelId;

    private Integer contextSize;

    private Integer threads;

    private Integer gpuLayers;

    private Integer batchSize;

    private Integer ubatchSize;

    private Boolean flashAttention;

    private Boolean memoryMap;

    private Boolean memoryLock;

    private Double temperature;

    private Double topP;

    private Integer topK;

    private Double minP;

    private Integer maxTokens;

    private Double repeatPenalty;

    private Integer repeatLastN;

    // seed 为空表示每次随机采样；默认更新策略会跳过 null，导致固定种子无法改回随机。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer seed;

    private Double frequencyPenalty;

    private Double presencePenalty;

    private String stopJson;

    private Boolean compressionEnabled;

    private Double compressionTriggerRatio;

    private Integer keepRecentMessages;

    private Integer summaryMaxTokens;

    private String advancedInferenceParams;

    private Long updatedAt;

    public static ModelConfigEntity defaults(String modelId) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setModelId(modelId);
        entity.setContextSize(4096);
        entity.setThreads(4);
        // 999 表示请求完整卸载，llamafile 会按模型实际层数和可用显存自行截断。
        entity.setGpuLayers(999);
        entity.setBatchSize(512);
        entity.setUbatchSize(512);
        entity.setFlashAttention(false);
        entity.setMemoryMap(true);
        entity.setMemoryLock(false);
        entity.setTemperature(0.7);
        entity.setTopP(0.9);
        entity.setTopK(40);
        entity.setMinP(0.05);
        entity.setMaxTokens(1024);
        entity.setRepeatPenalty(1.1);
        entity.setRepeatLastN(64);
        entity.setSeed(null);
        entity.setFrequencyPenalty(0.0);
        entity.setPresencePenalty(0.0);
        entity.setStopJson("[]");
        entity.setCompressionEnabled(true);
        entity.setCompressionTriggerRatio(0.75);
        entity.setKeepRecentMessages(8);
        entity.setSummaryMaxTokens(256);
        entity.setAdvancedInferenceParams("{}");
        entity.setUpdatedAt(System.currentTimeMillis());
        return entity;
    }
}
