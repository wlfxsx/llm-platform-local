package io.llmplatform.repository.convert;

import io.llmplatform.pojo.entity.McpServerRecord;
import io.llmplatform.pojo.entity.ModelRecord;
import io.llmplatform.pojo.entity.RagDocument;
import io.llmplatform.pojo.entity.SkillRecord;
import io.llmplatform.repository.PluginRepository;
import io.llmplatform.repository.entity.McpServerEntity;
import io.llmplatform.repository.entity.ModelEntity;
import io.llmplatform.repository.entity.PluginEntity;
import io.llmplatform.repository.entity.RagDocumentEntity;
import io.llmplatform.repository.entity.SkillEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 持久化实体与对外 record 的稳定转换。 */
@Mapper(componentModel = "spring")
public interface EntityConverters {

    ModelRecord toModelRecord(ModelEntity entity);

    ModelEntity toModelEntity(ModelRecord record);

    SkillRecord toSkillRecord(SkillEntity entity);

    SkillEntity toSkillEntity(SkillRecord record);

    McpServerRecord toMcpRecord(McpServerEntity entity);

    McpServerEntity toMcpEntity(McpServerRecord record);

    @Mapping(target = "importedAt", ignore = true)
    RagDocumentEntity toRagEntity(RagDocument document);

    RagDocument toRagDocument(RagDocumentEntity entity);

    @Mapping(target = "installedAt", ignore = true)
    PluginEntity toPluginEntity(PluginRepository.StoredPlugin plugin);

    PluginRepository.StoredPlugin toStoredPlugin(PluginEntity entity);
}
