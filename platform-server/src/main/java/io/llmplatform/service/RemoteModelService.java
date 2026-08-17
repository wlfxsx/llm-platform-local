package io.llmplatform.service;

import io.llmplatform.common.error.PlatformException;
import io.llmplatform.infra.secret.SecretStore;
import io.llmplatform.pojo.dto.RemoteModelWriteRequest;
import io.llmplatform.pojo.entity.AppSettings;
import io.llmplatform.pojo.vo.RemoteModelTestResult;
import io.llmplatform.pojo.vo.RemoteModelView;
import io.llmplatform.repository.RemoteModelRepository;
import io.llmplatform.repository.entity.RemoteModelEntity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 远程 OpenAI 兼容模型配置；密钥只写入系统凭据库。 */
@Service
@RequiredArgsConstructor
public class RemoteModelService {

    private final RemoteModelRepository repository;
    private final SecretStore secretStore;
    private final SettingsService settingsService;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public List<RemoteModelView> list() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    public RemoteModelView get(String id) {
        return toView(require(id));
    }

    /** 供聊天客户端读取完整连接信息；调用方不得把 apiKey 写入日志或 API 响应。 */
    public ResolvedRemoteModel resolve(String id) {
        RemoteModelEntity entity = require(id);
        String apiKey = secretStore.get(entity.getSecretRef());
        if (apiKey == null || apiKey.isBlank()) {
            throw new PlatformException("REMOTE_NOT_READY", "error.remoteApiKeyRequired");
        }
        return new ResolvedRemoteModel(
                entity.getId(),
                entity.getName(),
                normalizeBaseUrl(entity.getBaseUrl()),
                entity.getModelName(),
                apiKey);
    }

    public ResolvedRemoteModel resolveCurrent() {
        AppSettings settings = settingsService.current();
        if (!settings.remoteChat()) {
            throw new PlatformException("REMOTE_NOT_READY", "error.remoteNotSelected");
        }
        String id = settings.currentRemoteModelId();
        if (id == null || id.isBlank()) {
            throw new PlatformException("REMOTE_NOT_READY", "error.remoteNotSelected");
        }
        if (!settings.networkEnabled()) {
            throw new PlatformException("NETWORK_DISABLED", "error.networkDisabled");
        }
        return resolve(id);
    }

    @Transactional
    public RemoteModelView create(RemoteModelWriteRequest request) {
        String name = normalizeDisplayName(request.name());
        String key = normalizeKey(name);
        Optional<RemoteModelEntity> duplicate = repository.findByNormalizedName(key);
        if (duplicate.isPresent()) {
            return update(duplicate.get().getId(), request);
        }
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new PlatformException("INVALID_REQUEST", "error.remoteApiKeyRequired");
        }
        String id = UUID.randomUUID().toString();
        String secretRef = secretRef(id);
        secretStore.put(secretRef, request.apiKey().trim());
        long now = System.currentTimeMillis();
        RemoteModelEntity entity = new RemoteModelEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setNameNormalized(key);
        entity.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        entity.setModelName(request.modelName().trim());
        entity.setSecretRef(secretRef);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.insert(entity);
        selectIfEmpty(id);
        return toView(entity);
    }

    @Transactional
    public RemoteModelView update(String id, RemoteModelWriteRequest request) {
        RemoteModelEntity entity = require(id);
        String name = normalizeDisplayName(request.name());
        String key = normalizeKey(name);
        repository
                .findByNormalizedName(key)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(
                        existing -> {
                            throw new PlatformException("REMOTE_EXISTS", "error.remoteExists");
                        });
        entity.setName(name);
        entity.setNameNormalized(key);
        entity.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        entity.setModelName(request.modelName().trim());
        entity.setUpdatedAt(System.currentTimeMillis());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            secretStore.put(entity.getSecretRef(), request.apiKey().trim());
        } else if (!secretStore.exists(entity.getSecretRef())) {
            throw new PlatformException("INVALID_REQUEST", "error.remoteApiKeyRequired");
        }
        repository.update(entity);
        return toView(entity);
    }

    @Transactional
    public void delete(String id) {
        RemoteModelEntity entity = require(id);
        repository.deleteById(id);
        try {
            secretStore.delete(entity.getSecretRef());
        } catch (Exception ignored) {
            // 凭据已删或本就不存在时，配置行删除仍应成功。
        }
        AppSettings settings = settingsService.current();
        if (id.equals(settings.currentRemoteModelId())) {
            String next =
                    repository.findAll().stream()
                            .map(RemoteModelEntity::getId)
                            .findFirst()
                            .orElse("");
            settingsService.save(settings.withCurrentRemoteModelId(next));
        }
    }

    /** 探测 /v1/models；401/网络错误映射为可本地化 messageKey。 */
    public RemoteModelTestResult test(String id) {
        ResolvedRemoteModel remote = resolve(id);
        AppSettings settings = settingsService.current();
        if (!settings.networkEnabled()) {
            return new RemoteModelTestResult(false, "error.networkDisabled");
        }
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(remote.baseUrl() + "/models"))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer " + remote.apiKey())
                            .GET()
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return new RemoteModelTestResult(false, "error.remoteUnauthorized");
            }
            if (response.statusCode() >= 400) {
                return new RemoteModelTestResult(false, "error.remoteUnreachable");
            }
            return new RemoteModelTestResult(true, "status.remoteReady");
        } catch (Exception ex) {
            return new RemoteModelTestResult(false, "error.remoteUnreachable");
        }
    }

    public boolean currentReady() {
        try {
            resolveCurrent();
            return true;
        } catch (PlatformException ex) {
            return false;
        }
    }

    private void selectIfEmpty(String id) {
        AppSettings settings = settingsService.current();
        if (settings.currentRemoteModelId() == null || settings.currentRemoteModelId().isBlank()) {
            settingsService.save(settings.withCurrentRemoteModelId(id));
        }
    }

    private RemoteModelEntity require(String id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new PlatformException("NOT_FOUND", "error.notFound"));
    }

    private RemoteModelView toView(RemoteModelEntity entity) {
        return new RemoteModelView(
                entity.getId(),
                entity.getName(),
                entity.getBaseUrl(),
                entity.getModelName(),
                secretStore.exists(entity.getSecretRef()),
                entity.getCreatedAt() == null ? 0 : entity.getCreatedAt(),
                entity.getUpdatedAt() == null ? 0 : entity.getUpdatedAt());
    }

    private static String secretRef(String id) {
        return "remote-model/" + id;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        // 允许用户填 https://api.openai.com 或 .../v1
        if (!trimmed.endsWith("/v1")) {
            trimmed = trimmed + "/v1";
        }
        return trimmed;
    }

    private static String normalizeDisplayName(String name) {
        if (name == null || name.isBlank()) {
            throw new PlatformException("INVALID_REQUEST", "error.remoteNameInvalid");
        }
        return name.trim();
    }

    private static String normalizeKey(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    /** 已解析的远程连接；仅服务端内部使用。 */
    public record ResolvedRemoteModel(
            String id, String name, String baseUrl, String modelName, String apiKey) {}
}
