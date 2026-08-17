package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.RemoteModelEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RemoteModelMapper extends BaseMapper<RemoteModelEntity> {}
