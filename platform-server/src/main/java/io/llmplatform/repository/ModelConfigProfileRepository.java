package io.llmplatform.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.llmplatform.repository.entity.ModelConfigProfileEntity;
import io.llmplatform.repository.mapper.ModelConfigProfileMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 可命名参数策略的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class ModelConfigProfileRepository {

    private final ModelConfigProfileMapper mapper;

    public List<ModelConfigProfileEntity> findAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<ModelConfigProfileEntity>()
                        .orderByAsc(ModelConfigProfileEntity::getNameNormalized));
    }

    public Optional<ModelConfigProfileEntity> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<ModelConfigProfileEntity> findByNormalizedName(String nameNormalized) {
        return Optional.ofNullable(
                mapper.selectOne(
                        new LambdaQueryWrapper<ModelConfigProfileEntity>()
                                .eq(ModelConfigProfileEntity::getNameNormalized, nameNormalized)));
    }

    public void insert(ModelConfigProfileEntity entity) {
        mapper.insert(entity);
    }

    public void update(ModelConfigProfileEntity entity) {
        mapper.updateById(entity);
    }

    public void deleteById(String id) {
        mapper.deleteById(id);
    }
}
