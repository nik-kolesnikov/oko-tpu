package ru.tpu.oko.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.tpu.oko.catalog.repo.ProductStatus;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Query-параметры с кодами в нижнем регистре: {@code ?status=active&status=paused}. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, ProductStatus.class, ProductStatus::fromCode);
    }
}
