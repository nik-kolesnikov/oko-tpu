package ru.tpu.oko.catalog.api.dto;

import ru.tpu.oko.auth.api.UserDto;
import ru.tpu.oko.catalog.repo.ProductStatus;

import java.time.Instant;
import java.util.List;

/** Схема {@code Product}. */
public record ProductDto(long id, String name, String description, String url, ProductStatus status,
                         List<UserDto> owners, Instant createdAt, Instant updatedAt) {
}
