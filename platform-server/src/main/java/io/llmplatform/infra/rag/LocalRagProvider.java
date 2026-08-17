package io.llmplatform.infra.rag;

import io.llmplatform.common.UserDataPaths;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.pojo.entity.RagDocument;
import io.llmplatform.pojo.vo.RagChunk;
import io.llmplatform.pojo.vo.VectorHit;
import io.llmplatform.repository.RagRepository;
import io.llmplatform.repository.VectorStore;
import io.llmplatform.service.EmbeddingService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 本地文档切分、嵌入和检索。 */
@Component
public class LocalRagProvider implements RagProvider {

    private final RagRepository ragRepository;
    private final UserDataPaths paths;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public LocalRagProvider(
            RagRepository ragRepository,
            UserDataPaths paths,
            EmbeddingService embeddingService,
            VectorStore vectorStore) {
        this.ragRepository = ragRepository;
        this.paths = paths;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Override
    public String id() {
        return "local";
    }

    public List<RagDocument> listDocuments() {
        return ragRepository.findAllDocuments();
    }

    /** 文档元数据与切片写入处于数据库事务内；文件复制和向量存储不参与数据库回滚， 因此失败后允许保留可清理的本机文件，而不能提交部分数据库元数据。 */
    @Transactional
    public RagDocument importDocument(Path source) {
        paths.ensureDirectories();
        if (!Files.isRegularFile(source)) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
        try {
            String text = Files.readString(source);
            String id = UUID.randomUUID().toString();
            Path stored = paths.rag().resolve(id + "-" + source.getFileName());
            Files.copy(source, stored);
            List<String> chunks = chunk(text);
            RagDocument document =
                    new RagDocument(
                            id, source.getFileName().toString(), stored.toString(), chunks.size());
            ragRepository.insertDocument(document, System.currentTimeMillis());
            for (String chunk : chunks) {
                String chunkId = UUID.randomUUID().toString();
                ragRepository.insertChunk(chunkId, id, chunk);
                vectorStore.upsert("rag", chunkId, embeddingService.current().embed(chunk), chunk);
            }
            return document;
        } catch (IOException ex) {
            throw new PlatformException("FILE_COPY_FAILED", "error.fileCopyFailed");
        }
    }

    @Transactional
    public void deleteDocument(String id) {
        ragRepository.deleteDocument(id);
    }

    @Override
    public List<RagChunk> retrieve(String query, int topK) {
        float[] embedding = embeddingService.current().embed(query);
        List<VectorHit> hits = vectorStore.search("rag", embedding, topK);
        List<RagChunk> chunks = new ArrayList<>();
        for (VectorHit hit : hits) {
            chunks.add(new RagChunk(hit.id(), "", hit.payload(), hit.score()));
        }
        return chunks;
    }

    private List<String> chunk(String text) {
        // 当前固定字符窗口只是离线最小闭环，后续可替换为分词切分而不改变 Repository 契约。
        List<String> chunks = new ArrayList<>();
        int size = 600;
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        if (chunks.isEmpty()) {
            chunks.add(text);
        }
        return chunks;
    }
}
