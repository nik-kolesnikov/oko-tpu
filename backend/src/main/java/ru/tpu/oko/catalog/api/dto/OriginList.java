package ru.tpu.oko.catalog.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Схема {@code OriginList}; она же тело PUT /origins. */
public record OriginList(
        @NotNull(message = "обязательное поле")
        @Size(max = 50, message = "не больше 50 сайтов")
        List<String> origins) {
}
