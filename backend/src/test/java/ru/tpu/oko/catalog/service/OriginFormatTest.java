package ru.tpu.oko.catalog.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OriginFormatTest {

    @ParameterizedTest
    @CsvSource({
            "https://lk.tpu.ru, https://lk.tpu.ru",
            "HTTPS://LK.TPU.RU, https://lk.tpu.ru",
            "https://lk.tpu.ru:443, https://lk.tpu.ru",
            "http://localhost:80, http://localhost",
            "http://localhost:3000, http://localhost:3000",
            "https://my-site.tpu.ru:8443, https://my-site.tpu.ru:8443",
            "http://10.0.0.5:8080, http://10.0.0.5:8080",
            "'  https://lk.tpu.ru  ', https://lk.tpu.ru"
    })
    void normalizesValidOrigins(String raw, String expected) {
        assertThat(OriginFormat.normalize(raw)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.tpu.ru", "https://lk.tpu.ru/", "https://lk.tpu.ru/path", "lk.tpu.ru",
            "ftp://lk.tpu.ru", "https://lk.tpu.ru?x=1", "https://user@lk.tpu.ru", "https://lk.tpu.ru:0",
            "https://lk.tpu.ru:70000", "https://-lk.tpu.ru", "https://lk..tpu.ru", "null", ""})
    void rejectsInvalidOrigins(String raw) {
        assertThat(OriginFormat.normalize(raw)).isEmpty();
    }
}
