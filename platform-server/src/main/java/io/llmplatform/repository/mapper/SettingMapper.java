package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.SettingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 应用键值设置 Mapper，使用 SQLite upsert 保持单键单行。 */
@Mapper
public interface SettingMapper extends BaseMapper<SettingEntity> {

    /** 读取原始 JSON 文本，反序列化策略由 Repository 控制。 */
    String selectValue(@Param("key") String key);

    /** 原子插入或替换指定设置值。 */
    int upsertValue(@Param("key") String key, @Param("value") String value);
}
