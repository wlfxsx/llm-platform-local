package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.CapabilityEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 全局能力开关 Mapper，SQLite 方言 upsert 由 XML 明确定义。 */
@Mapper
public interface CapabilityMapper extends BaseMapper<CapabilityEntity> {

    /** 将 SQLite 0/1 归一化为可空布尔值，空值表示尚未配置。 */
    Boolean selectEnabled(@Param("id") String id);

    /** 插入或覆盖全局能力开关。 */
    int upsertEnabled(@Param("id") String id, @Param("enabled") int enabled);

    /** 只在首次启动时写入默认值，不覆盖用户已有选择。 */
    int insertDefault(@Param("id") String id, @Param("enabled") int enabled);
}
