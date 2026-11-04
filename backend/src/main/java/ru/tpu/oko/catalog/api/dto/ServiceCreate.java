package ru.tpu.oko.catalog.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Схема {@code ServiceCreate}. */
public record ServiceCreate(
        @NotNull(message = "обязательное поле")
        @Size(min = 1, max = 200, message = "от 1 до 200 символов")
        @Pattern(regexp = "(?s).*\\S.*", message = "не должно состоять из пробелов")
        String name,
        String url,
        @Size(max = 200, message = "не длиннее 200 символов")
        String monitoringRef) {
}
