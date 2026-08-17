package io.llmplatform.service;

import io.llmplatform.common.UserDataPaths;
import io.llmplatform.common.error.PlatformException;
import io.llmplatform.infra.llamafile.LlamafileManager;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.pojo.entity.ModelRecord;
import io.llmplatform.repository.ModelRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 导入、列举和删除本地模型文件。 */
@Service
public class ModelService {

    private final ModelRepository modelRepository;
    private final UserDataPaths paths;
    private final SettingsService settingsService;
    private final LlamafileManager llamafileManager;
    private final ModelConfigService modelConfigService;

    public ModelService(
            ModelRepository modelRepository,
            UserDataPaths paths,
            SettingsService settingsService,
            LlamafileManager llamafileManager,
            ModelConfigService modelConfigService) {
        this.modelRepository = modelRepository;
        this.paths = paths;
        this.settingsService = settingsService;
        this.llamafileManager = llamafileManager;
        this.modelConfigService = modelConfigService;
    }

    public List<ModelRecord> list() {
        return modelRepository.findAll();
    }

    @Transactional
    public ModelRecord importFile(Path source) {
        paths.ensureDirectories();
        if (!Files.isRegularFile(source)) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
        String id = UUID.randomUUID().toString();
        String name = source.getFileName().toString();
        Path target = paths.models().resolve(id + "-" + name);
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new PlatformException("FILE_COPY_FAILED", "error.fileCopyFailed");
        }
        ModelRecord model =
                new ModelRecord(id, name, target.toString(), System.currentTimeMillis());
        modelRepository.insert(model);
        modelConfigService.createDefault(id);
        return model;
    }

    public void select(String id) {
        llamafileManager.requireStopped();
        ModelRecord model = require(id);
        modelConfigService.ensureEntity(id);
        settingsService.save(settingsService.current().withCurrentModelId(model.id()));
    }

    @Transactional
    public void delete(String id) {
        ModelRecord model = require(id);
        AppSettings current = settingsService.current();
        boolean deletingCurrent = id.equals(current.currentModelId());
        // 当前模型运行时必须先停止；检查必须发生在删除数据库记录和模型文件之前。
        if (deletingCurrent) {
            llamafileManager.requireStopped();
        }
        modelConfigService.delete(id);
        modelRepository.deleteById(id);
        try {
            Files.deleteIfExists(Path.of(model.filePath()));
        } catch (IOException ignored) {
            // 数据库记录已删除，文件残留不影响后续导入。
        }
        if (deletingCurrent) {
            settingsService.save(current.withCurrentModelId(""));
        }
    }

    private ModelRecord require(String id) {
        return modelRepository
                .findById(id)
                .orElseThrow(() -> new PlatformException("NOT_FOUND", "error.notFound"));
    }
}
