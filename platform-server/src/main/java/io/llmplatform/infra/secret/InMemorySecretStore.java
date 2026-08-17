package io.llmplatform.infra.secret;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 测试用内存凭据库。 */
public final class InMemorySecretStore implements SecretStore {

    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    @Override
    public void put(String ref, String secret) {
        secrets.put(ref, secret);
    }

    @Override
    public String get(String ref) {
        return secrets.get(ref);
    }

    @Override
    public void delete(String ref) {
        secrets.remove(ref);
    }

    @Override
    public boolean exists(String ref) {
        return secrets.containsKey(ref);
    }
}
