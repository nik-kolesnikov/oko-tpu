package ru.tpu.oko.auth.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnToTest {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/products/12", "/products?tab=keys#x"})
    void keepsRelativePaths(String path) {
        assertThat(ReturnTo.sanitize(path)).isEqualTo(path);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"//evil.example", "https://evil.example", "/\\evil.example", "evil", "/a\\b",
            "/a\r\nSet-Cookie: x=1", "javascript:alert(1)"})
    void replacesEverythingElseWithRoot(String path) {
        assertThat(ReturnTo.sanitize(path)).isEqualTo("/");
    }
}
