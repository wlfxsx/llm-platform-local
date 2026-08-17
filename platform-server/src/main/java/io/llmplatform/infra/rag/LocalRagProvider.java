package io.llmplatform.infra.rag;

import io.llmplatform.common.UserDataPaths;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.infra.embedding.EmbeddingProvider;
import io.llmplatform.pojo.entity.RagDocument;
import io.llmplatform.pojo.vo.RagChunk;
import io.llmplatform.repository.GraphRagRepository;
import io.llmplatform.repository.RagRepository;
import io.llmplatform.repository.VectorStore;
import io.llmplatform.repository.entity.RagChunkEntity;
import io.llmplatform.service.EmbeddingService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 本地文档抽取、切分、嵌入和混合检索。 */
@Component
public class LocalRagProvider implements RagProvider {

    private final RagRepository ragRepository;
    private final GraphRagRepository graphRagRepository;
    private final UserDataPaths paths;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final DocumentTextExtractor extractor;
    private final HybridRagRetriever hybridRagRetriever;
    private final GraphRagIndexer graphRagIndexer;

    public LocalRagProvider(
            RagRepository ragRepository,
            GraphRagRepository graphRagRepository,
            UserDataPaths paths,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            DocumentTextExtractor extractor,
            HybridRagRetriever hybridRagRetriever,
            GraphRagIndexer graphRagIndexer) {
        this.ragRepository = ragRepository;
        this.graphRagRepository = graphRagRepository;
        this.paths = paths;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.extractor = extractor;
        this.hybridRagRetriever = hybridRagRetriever;
        this.graphRagIndexer = graphRagIndexer;
    }

    @Override
    public String id() {
        return "local";
    }

    public List<RagDocument> listDocuments() {
        return ragRepository.findAllDocuments();
    }

    /** 文档元数据与切片写入处于数据库事务内；文件复制和向量存储不参与数据库回滚。 */
    @Transactional
    public RagDocument importDocument(Path source) {
        return importDocument(source, "");
    }

    @Transactional
    public RagDocument importDocument(Path source, String sessionId) {
        paths.ensureDirectories();
        ExtractedDocument extracted = extractor.extract(source);
        EmbeddingProvider embedding = embeddingService.current();
        requireCompatibleDimension(embedding);
        try {
            String id = UUID.randomUUID().toString();
            Path stored = paths.rag().resolve(id + "-" + source.getFileName());
            Files.copy(source, stored);
            List<ChunkDraft> drafts =
                    DocumentChunker.chunk(extracted.text(), extracted.kind(), extracted.title());
            try {
                drafts = SemanticChunkMerger.merge(drafts, embedding);
            } catch (RuntimeException ignored) {
                // 嵌入未就绪时保留结构切分结果。
            }
            List<DocumentChunker.ParentGroup> groups = DocumentChunker.groupParents(drafts);
            int childCount = drafts.size();
            RagDocument document =
                    new RagDocument(id, extracted.title(), stored.toString(), childCount);
            ragRepository.insertDocument(document, System.currentTimeMillis());
            int ordinal = 0;
            int parentOrdinal = 0;
            for (DocumentChunker.ParentGroup group : groups) {
                String parentId = null;
                if (group.children().size() > 1) {
                    parentId = UUID.randomUUID().toString();
                    RagChunkEntity parent = new RagChunkEntity();
                    parent.setId(parentId);
                    parent.setDocumentId(id);
                    parent.setContent(group.combinedBody());
                    parent.setOrdinal(parentOrdinal++);
                    parent.setHeadingPath(group.headingPath());
                    parent.setCharStart(group.children().getFirst().charStart());
                    parent.setCharEnd(group.children().getLast().charEnd());
                    parent.setRole("parent");
                    ragRepository.insertChunk(parent);
                }
                for (ChunkDraft draft : group.children()) {
                    String chunkId = UUID.randomUUID().toString();
                    String content = draft.prefixed(extracted.title());
                    RagChunkEntity child = new RagChunkEntity();
                    child.setId(chunkId);
                    child.setDocumentId(id);
                    child.setContent(content);
                    child.setOrdinal(ordinal++);
                    child.setHeadingPath(draft.headingPath());
                    child.setParentId(parentId);
                    child.setCharStart(draft.charStart());
                    child.setCharEnd(draft.charEnd());
                    child.setRole("child");
                    ragRepository.insertChunk(child);
                    float[] vector = embedding.embedDocument(content);
                    vectorStore.upsert("rag", chunkId, vector, content);
                }
            }
            graphRagIndexer.schedule(id, sessionId);
            return document;
        } catch (IOException ex) {
            throw new PlatformException("FILE_COPY_FAILED", "error.fileCopyFailed");
        }
    }

    @Transactional
    public void deleteDocument(String id) {
        graphRagRepository.deleteByDocument(id);
        ragRepository.deleteDocument(id);
    }

    @Override
    public List<RagChunk> retrieve(String query, int topK) {
        return retrieve(query, topK, "");
    }

    @Override
    public List<RagChunk> retrieve(String query, int topK, String sessionId) {
        return hybridRagRetriever.retrieve(query, sessionId, topK);
    }

    private void requireCompatibleDimension(EmbeddingProvider embedding) {
        int storedBytes = ragRepository.storedEmbeddingByteLength();
        if (storedBytes <= 0) {
            return;
        }
        float[] probe = embedding.embedDocument("dimension-check");
        if (probe.length * 4 != storedBytes) {
            throw new PlatformException(
                    "RAG_DIMENSION_MISMATCH", "error.ragEmbeddingDimensionMismatch");
        }
    }
}
