package ru.tpu.oko.catalog.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchPatternTest {

    @Test
    void escapesLikeWildcardsAndLowercases() {
        assertThat(ProductService.likePattern(null)).isEqualTo("%");
        assertThat(ProductService.likePattern("  ")).isEqualTo("%");
        assertThat(ProductService.likePattern("ЛК")).isEqualTo("%лк%");
        assertThat(ProductService.likePattern("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%");
    }
}
