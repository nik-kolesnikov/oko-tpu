package ru.tpu.oko.widget.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSecurityPolicyTest {

    @Test
    void frameAncestorsComeFromAllowedOrigins() {
        assertThat(WidgetService.contentSecurityPolicy(List.of("https://lk.tpu.ru", "https://rasp.tpu.ru")))
                .startsWith("frame-ancestors https://lk.tpu.ru https://rasp.tpu.ru;")
                .contains("script-src 'self'");
    }

    @Test
    void emptyListForbidsEmbedding() {
        assertThat(WidgetService.contentSecurityPolicy(List.of())).startsWith("frame-ancestors 'none';");
    }
}
