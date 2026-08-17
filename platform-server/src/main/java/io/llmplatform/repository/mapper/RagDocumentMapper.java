package io.llmplatform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.llmplatform.repository.entity.RagDocumentEntity;
import org.apache.ibatis.annotations.Mapper;

/** RAG 文档元数据单表 CRUD；切片和向量由各自边界维护。 */
@Mapper
public interface RagDocumentMapper extends BaseMapper<RagDocumentEntity> {}
