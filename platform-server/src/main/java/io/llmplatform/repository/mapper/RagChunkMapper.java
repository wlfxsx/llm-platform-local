package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.RagChunkEntity;
import org.apache.ibatis.annotations.Mapper;

/** RAG 文本切片 CRUD；embedding BLOB 保留给 JDBC 向量存储处理。 */
@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunkEntity> {}
