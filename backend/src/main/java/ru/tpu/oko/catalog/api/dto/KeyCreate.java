package ru.tpu.oko.catalog.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.tpu.oko.catalog.repo.KeyType;

public record KeyCreate(@NotNull(message = "обязательное поле") KeyType type) {
}
