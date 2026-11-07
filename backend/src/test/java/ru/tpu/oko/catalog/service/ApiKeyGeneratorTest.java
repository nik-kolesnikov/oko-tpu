package ru.tpu.oko.catalog.service;

import org.junit.jupiter.api.Test;
import ru.tpu.oko.catalog.repo.KeyType;
import ru.tpu.oko.common.crypto.Crypto;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyGeneratorTest {

    @Test
    void keysHaveTypePrefix256BitsAndStoredHash() {
        ApiKeyGenerator.Generated pk = ApiKeyGenerator.generate(KeyType.PUBLIC);
        ApiKeyGenerator.Generated sk = ApiKeyGenerator.generate(KeyType.SECRET);
        assertThat(pk.raw()).startsWith("pk_").hasSize(3 + 43);
        assertThat(sk.raw()).startsWith("sk_").hasSize(3 + 43);
        assertThat(pk.prefix()).isEqualTo(pk.raw().substring(0, 11));
        assertThat(pk.hash()).isEqualTo(Crypto.sha256(pk.raw()));
        assertThat(ApiKeyGenerator.generate(KeyType.PUBLIC).raw()).isNotEqualTo(pk.raw());
    }
}
