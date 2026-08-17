package io.llmplatform.infra.secret;

/** 跨平台密钥存储；远程 API Key 只走系统凭据库，不落 SQLite 明文。 */
public interface SecretStore {

    void put(String ref, String secret);

    String get(String ref);

    void delete(String ref);

    boolean exists(String ref);
}
