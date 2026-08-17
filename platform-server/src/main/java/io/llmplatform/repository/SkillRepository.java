package io.llmplatform.repository;

import io.llmplatform.pojo.entity.SkillRecord;
import io.llmplatform.repository.convert.EntityConverters;
import io.llmplatform.repository.entity.SkillEntity;
import io.llmplatform.repository.mapper.SkillMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 技能包记录的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class SkillRepository {

    private final SkillMapper skillMapper;
    private final EntityConverters converters;

    public List<SkillRecord> findAll() {
        return skillMapper.selectList(null).stream().map(converters::toSkillRecord).toList();
    }

    public Optional<SkillRecord> findById(String id) {
        return Optional.ofNullable(skillMapper.selectById(id)).map(converters::toSkillRecord);
    }

    public void insert(SkillRecord skill) {
        skillMapper.insert(converters.toSkillEntity(skill));
    }

    /** 返回受影响行数，调用方据此判断记录是否存在。 */
    public int updateEnabled(String id, boolean enabled) {
        SkillEntity entity = new SkillEntity();
        entity.setId(id);
        entity.setEnabled(enabled);
        return skillMapper.updateById(entity);
    }

    public void deleteById(String id) {
        skillMapper.deleteById(id);
    }
}
