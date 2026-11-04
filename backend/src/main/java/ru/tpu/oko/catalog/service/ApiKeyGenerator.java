package ru.tpu.oko.catalog.service;

import ru.tpu.oko.catalog.repo.KeyType;
import ru.tpu.oko.common.crypto.Crypto;

/** Ключ: pk_/sk_ + 256 бит SecureRandom в base64url. В БД — только SHA-256 и префикс. */
public final class ApiKeyGenerator {

    /** Сколько символов случайной части показываем в keyPrefix (после pk_/sk_). */
    static final int PREFIX_RANDOM_CHARS = 8;

    private ApiKeyGenerator() {
    }

    public static Generated generate(KeyType type) {
        String raw = prefixOf(type) + Crypto.randomToken(32);
        return new Generated(raw, raw.substring(0, 3 + PREFIX_RANDOM_CHARS), Crypto.sha256(raw));
    }

    public static String prefixOf(KeyType type) {
        return type == KeyType.PUBLIC ? "pk_" : "sk_";
    }

    public record Generated(String raw, String prefix, byte[] hash) {
    }
}
