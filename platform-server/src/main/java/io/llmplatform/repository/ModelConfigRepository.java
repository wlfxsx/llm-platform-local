package io.llmplatform.repository;

import io.llmplatform.repository.entity.ModelConfigEntity;
import io.llmplatform.repository.mapper.ModelConfigMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 每个模型独立运行参数的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class ModelConfigRepository {

    private final ModelConfigMapper modelConfigMapper;

    public Optional<ModelConfigEntity> findByModelId(String modelId) {
        return Optional.ofNullable(modelConfigMapper.selectById(modelId));
    }

    public void insert(ModelConfigEntity entity) {
        modelConfigMapper.insert(entity);
    }

    public void update(ModelConfigEntity entity) {
        modelConfigMapper.updateById(entity);
    }

    public void deleteByModelId(String modelId) {
        modelConfigMapper.deleteById(modelId);
    }
}
