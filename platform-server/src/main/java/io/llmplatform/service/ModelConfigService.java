package io.llmplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.infra.llamafile.LlamafileManager;
import io.llmplatform.pojo.dto.ModelConfigUpdateRequest;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.pojo.vo.ModelConfigView;
import io.llmplatform.repository.ModelConfigRepository;
import io.llmplatform.repository.ModelRepository;
import io.llmplatform.repository.entity.ModelConfigEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 每个模型一份独立配置；切换模型时自动使用对应参数。 */
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;
    private final ModelRepository modelRepository;
    private final SettingsService settingsService;
    private final LlamafileManager llamafileManager;
    private final AdvancedInferenceParamsValidator advancedValidator;
    private final ObjectMapper objectMapper;

    public ModelConfigView get(String modelId) {
        requireModel(modelId);
        return toView(ensureEntity(modelId), false);
    }

    public ModelConfigView current() {
        String modelId = settingsService.current().currentModelId();
        if (modelId == null || modelId.isBlank()) {
            throw new PlatformException("MODEL_NOT_READY", "error.modelNotReady");
        }
        return get(modelId);
    }

    /** 推理用的采样/压缩参数：本地走当前模型配置；远程无本地模型时回退到安全默认值。 */
    public ModelConfigView currentForInference() {
        AppSettings settings = settingsService.current();
        if (settings.remoteChat()) {
            String modelId = settings.currentModelId();
            if (modelId != null
                    && !modelId.isBlank()
                    && modelRepository.findById(modelId).isPresent()) {
                return get(modelId);
            }
            return toView(ModelConfigEntity.defaults(""), false);
        }
        return current();
    }

    /** 保存整份模型配置。运行中的当前模型禁止改参；采样和压缩由下一次请求读取，运行参数在下次显式启动后生效。 */
    @Transactional
    public ModelConfigView save(String modelId, ModelConfigUpdateRequest request) {
        requireModel(modelId);
        AppSettings settings = settingsService.current();
        if (modelId.equals(settings.currentModelId())) {
            llamafileManager.requireStopped();
        }
        ModelConfigEntity existing = ensureEntity(modelId);
        apply(existing, request);
        existing.setUpdatedAt(System.currentTimeMillis());
        modelConfigRepository.update(existing);
        return toView(existing, false);
    }

    /** 为旧数据或异常缺失行按需补建默认配置，使模型切换不依赖额外初始化顺序。 */
    public ModelConfigEntity ensureEntity(String modelId) {
        return modelConfigRepository
                .findByModelId(modelId)
                .orElseGet(
                        () -> {
                            ModelConfigEntity created = ModelConfigEntity.defaults(modelId);
                            modelConfigRepository.insert(created);
                            return created;
                        });
    }

    public void createDefault(String modelId) {
        if (modelConfigRepository.findByModelId(modelId).isEmpty()) {
            modelConfigRepository.insert(ModelConfigEntity.defaults(modelId));
        }
    }

    public void delete(String modelId) {
        modelConfigRepository.deleteByModelId(modelId);
    }

    /** 把策略或表单中的完整参数投影成与模型配置相同的视图，便于桌面直接套用。 */
    public ModelConfigView asView(ModelConfigUpdateRequest request) {
        ModelConfigEntity entity = ModelConfigEntity.defaults("");
        apply(entity, request);
        entity.setModelId("");
        return toView(entity, false);
    }

    private void requireModel(String modelId) {
        modelRepository
                .findById(modelId)
                .orElseThrow(() -> new PlatformException("NOT_FOUND", "error.notFound"));
    }

    private void apply(ModelConfigEntity entity, ModelConfigUpdateRequest request) {
        // 高级字段先通过统一校验，再与强类型字段一起持久化，避免绕过保留键限制。
        JsonNode advanced = advancedValidator.normalize(request.advancedInferenceParams());
        entity.setContextSize(request.contextSize());
        entity.setThreads(request.threads());
        entity.setGpuLayers(request.gpuLayers());
        entity.setBatchSize(request.batchSize());
        entity.setUbatchSize(request.ubatchSize());
        entity.setFlashAttention(request.flashAttention());
        entity.setMemoryMap(request.memoryMap());
        entity.setMemoryLock(request.memoryLock());
        entity.setTemperature(request.temperature());
        entity.setTopP(request.topP());
        entity.setTopK(request.topK());
        entity.setMinP(request.minP());
        entity.setMaxTokens(request.maxTokens());
        entity.setRepeatPenalty(request.repeatPenalty());
        entity.setRepeatLastN(request.repeatLastN());
        entity.setSeed(request.seed());
        entity.setFrequencyPenalty(request.frequencyPenalty());
        entity.setPresencePenalty(request.presencePenalty());
        entity.setStopJson(writeJson(request.stop() == null ? List.of() : request.stop()));
        entity.setCompressionEnabled(request.compressionEnabled());
        entity.setCompressionTriggerRatio(request.compressionTriggerRatio());
        entity.setKeepRecentMessages(request.keepRecentMessages());
        entity.setSummaryMaxTokens(request.summaryMaxTokens());
        entity.setAdvancedInferenceParams(writeJson(advanced));
    }

    /** 将数据库 JSON 字段解析为稳定的 API 结构，并把历史空值归一化为安全默认值。 */
    public ModelConfigView toView(ModelConfigEntity entity, boolean processRestarted) {
        return new ModelConfigView(
                entity.getModelId(),
                entity.getContextSize(),
                entity.getThreads(),
                entity.getGpuLayers(),
                entity.getBatchSize(),
                entity.getUbatchSize(),
                Boolean.TRUE.equals(entity.getFlashAttention()),
                !Boolean.FALSE.equals(entity.getMemoryMap()),
                Boolean.TRUE.equals(entity.getMemoryLock()),
                entity.getTemperature(),
                entity.getTopP(),
                entity.getTopK(),
                entity.getMinP(),
                entity.getMaxTokens(),
                entity.getRepeatPenalty(),
                entity.getRepeatLastN(),
                entity.getSeed(),
                entity.getFrequencyPenalty(),
                entity.getPresencePenalty(),
                readStringList(entity.getStopJson()),
                !Boolean.FALSE.equals(entity.getCompressionEnabled()),
                entity.getCompressionTriggerRatio(),
                entity.getKeepRecentMessages(),
                entity.getSummaryMaxTokens(),
                readTree(entity.getAdvancedInferenceParams()),
                processRestarted);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
    }

    private JsonNode readTree(String json) {
        try {
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            // 历史损坏配置不能阻断设置页读取；返回空对象后可由用户重新保存修复。
            return objectMapper.createObjectNode();
        }
    }

    private List<String> readStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            // stop 只是可选推理参数，历史格式异常时按未配置处理。
            return List.of();
        }
    }
}
