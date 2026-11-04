package ru.tpu.oko.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Схема {@code ProductCreate}. */
public record ProductCreate(
        @NotNull(message = "обязательное поле")
        @Size(min = 2, max = 200, message = "от 2 до 200 символов")
        @Pattern(regexp = "(?s).*\\S.*", message = "не должно состоять из пробелов")
        String name,
        String description,
        @NotBlank(message = "обязательное поле")
        String url,
        /** Только для админа: логины ТПУ владельцев (минимум один). */
        List<String> ownerLogins) {
}
