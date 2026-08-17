package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 知识库文档行。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("rag_documents")
public class RagDocumentEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String title;

    private String filePath;

    private Integer chunkCount;

    private Long importedAt;
}
