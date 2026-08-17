package io.llmplatform.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.llmplatform.repository.entity.RemoteModelEntity;
import io.llmplatform.repository.mapper.RemoteModelMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 远程模型配置持久化。 */
@Repository
@RequiredArgsConstructor
public class RemoteModelRepository {

    private final RemoteModelMapper mapper;

    public List<RemoteModelEntity> findAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<RemoteModelEntity>()
                        .orderByAsc(RemoteModelEntity::getNameNormalized));
    }

    public Optional<RemoteModelEntity> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<RemoteModelEntity> findByNormalizedName(String nameNormalized) {
        return Optional.ofNullable(
                mapper.selectOne(
                        new LambdaQueryWrapper<RemoteModelEntity>()
                                .eq(RemoteModelEntity::getNameNormalized, nameNormalized)));
    }

    public void insert(RemoteModelEntity entity) {
        mapper.insert(entity);
    }

    public void update(RemoteModelEntity entity) {
        mapper.updateById(entity);
    }

    public void deleteById(String id) {
        mapper.deleteById(id);
    }
}
