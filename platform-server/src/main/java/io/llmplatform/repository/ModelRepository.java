package io.llmplatform.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.llmplatform.pojo.entity.ModelRecord;
import io.llmplatform.repository.convert.EntityConverters;
import io.llmplatform.repository.entity.ModelEntity;
import io.llmplatform.repository.mapper.ModelMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 本地模型文件记录的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class ModelRepository {

    private final ModelMapper modelMapper;
    private final EntityConverters converters;

    public List<ModelRecord> findAll() {
        return modelMapper
                .selectList(
                        Wrappers.<ModelEntity>lambdaQuery().orderByDesc(ModelEntity::getImportedAt))
                .stream()
                .map(converters::toModelRecord)
                .toList();
    }

    public Optional<ModelRecord> findById(String id) {
        return Optional.ofNullable(modelMapper.selectById(id)).map(converters::toModelRecord);
    }

    public void insert(ModelRecord model) {
        modelMapper.insert(converters.toModelEntity(model));
    }

    public void deleteById(String id) {
        modelMapper.deleteById(id);
    }
}
