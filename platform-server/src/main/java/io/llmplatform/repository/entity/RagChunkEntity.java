package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 知识库切片行；向量 BLOB 仍由 JDBC VectorStore 维护。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("rag_chunks")
public class RagChunkEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String documentId;

    private String content;

    private Integer ordinal;

    private String headingPath;

    private String parentId;

    private Integer charStart;

    private Integer charEnd;

    /** child 参与检索；parent 仅用于注入扩展。 */
    private String role;
}
