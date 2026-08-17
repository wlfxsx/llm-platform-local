package io.llmplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.dto.ModelConfigProfileWriteRequest;
import io.llmplatform.pojo.dto.ModelConfigUpdateRequest;
import io.llmplatform.pojo.vo.ModelConfigProfileView;
import io.llmplatform.repository.ModelConfigProfileRepository;
import io.llmplatform.repository.entity.ModelConfigEntity;
import io.llmplatform.repository.entity.ModelConfigProfileEntity;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 与具体模型解耦的可命名参数策略；模型必须按某个策略启动。 */
@Service
@RequiredArgsConstructor
public class ModelConfigProfileService {

    /** 首次运行自举的预设名；保证任何时候都有一个可用于启动的预设。 */
    private static final String DEFAULT_PROFILE_NAME = "默认";

    private final ModelConfigProfileRepository repository;
    private final ModelConfigService modelConfigService;
    private final SettingsService settingsService;
    private final AdvancedInferenceParamsValidator advancedValidator;
    private final ObjectMapper objectMapper;

    public List<ModelConfigProfileView> list() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    public ModelConfigProfileView get(String id) {
        return toView(require(id));
    }

    /** 策略列表为空时补一条安全默认值，避免用户删光后无法启动模型。 */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureDefault() {
        if (!repository.findAll().isEmpty()) {
            return;
        }
        create(new ModelConfigProfileWriteRequest(DEFAULT_PROFILE_NAME, defaultConfig()));
    }

    /** 把策略参数写入当前模型配置，供启动前套用。 */
    @Transactional
    public void applyToCurrentModel(String profileId) {
        String modelId = settingsService.current().currentModelId();
        if (modelId == null || modelId.isBlank()) {
            throw new PlatformException("MODEL_NOT_READY", "error.modelNotReady");
        }
        modelConfigService.save(modelId, readParams(require(profileId).getParamsJson()));
    }

    /** 同名策略视为覆盖保存，与桌面端“保存为预设”一致。 */
    @Transactional
    public ModelConfigProfileView create(ModelConfigProfileWriteRequest request) {
        String name = normalizeDisplayName(request.name());
        String key = normalizeKey(name);
        Optional<ModelConfigProfileEntity> duplicate = repository.findByNormalizedName(key);
        if (duplicate.isPresent()) {
            return update(duplicate.get().getId(), request);
        }
        validateConfig(request.config());
        long now = System.currentTimeMillis();
        ModelConfigProfileEntity entity = new ModelConfigProfileEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name);
        entity.setNameNormalized(key);
        entity.setParamsJson(writeParams(request.config()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.insert(entity);
        return toView(entity);
    }

    @Transactional
    public ModelConfigProfileView update(String id, ModelConfigProfileWriteRequest request) {
        ModelConfigProfileEntity entity = require(id);
        String name = normalizeDisplayName(request.name());
        String key = normalizeKey(name);
        repository
                .findByNormalizedName(key)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(
                        existing -> {
                            throw new PlatformException("PROFILE_EXISTS", "error.profileExists");
                        });
        validateConfig(request.config());
        entity.setName(name);
        entity.setNameNormalized(key);
        entity.setParamsJson(writeParams(request.config()));
        entity.setUpdatedAt(System.currentTimeMillis());
        repository.update(entity);
        return toView(entity);
    }

    @Transactional
    public void delete(String id) {
        require(id);
        repository.deleteById(id);
        ensureDefault();
    }

    private ModelConfigUpdateRequest defaultConfig() {
        ModelConfigEntity defaults = ModelConfigEntity.defaults("");
        return new ModelConfigUpdateRequest(
                defaults.getContextSize(),
                defaults.getThreads(),
                defaults.getGpuLayers(),
                defaults.getBatchSize(),
                defaults.getUbatchSize(),
                defaults.getFlashAttention(),
                defaults.getMemoryMap(),
                defaults.getMemoryLock(),
                defaults.getTemperature(),
                defaults.getTopP(),
                defaults.getTopK(),
                defaults.getMinP(),
                defaults.getMaxTokens(),
                defaults.getRepeatPenalty(),
                defaults.getRepeatLastN(),
                defaults.getSeed(),
                defaults.getFrequencyPenalty(),
                defaults.getPresencePenalty(),
                List.of(),
                defaults.getCompressionEnabled(),
                defaults.getCompressionTriggerRatio(),
                defaults.getKeepRecentMessages(),
                defaults.getSummaryMaxTokens(),
                objectMapper.createObjectNode());
    }

    private ModelConfigProfileEntity require(String id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new PlatformException("NOT_FOUND", "error.notFound"));
    }

    private ModelConfigProfileView toView(ModelConfigProfileEntity entity) {
        ModelConfigUpdateRequest config = readParams(entity.getParamsJson());
        return new ModelConfigProfileView(
                entity.getId(),
                entity.getName(),
                modelConfigService.asView(config),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private void validateConfig(ModelConfigUpdateRequest config) {
        advancedValidator.normalize(config.advancedInferenceParams());
    }

    private String writeParams(ModelConfigUpdateRequest config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception ex) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
    }

    private ModelConfigUpdateRequest readParams(String json) {
        try {
            return objectMapper.readValue(json, ModelConfigUpdateRequest.class);
        } catch (Exception ex) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
    }

    private static String normalizeDisplayName(String name) {
        if (name == null) {
            throw new PlatformException("INVALID_REQUEST", "error.profileNameInvalid");
        }
        String trimmed = name.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new PlatformException("INVALID_REQUEST", "error.profileNameInvalid");
        }
        return trimmed;
    }

    private static String normalizeKey(String displayName) {
        return Normalizer.normalize(displayName, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
