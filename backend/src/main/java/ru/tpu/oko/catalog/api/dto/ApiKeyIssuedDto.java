package ru.tpu.oko.catalog.api.dto;

import ru.tpu.oko.auth.api.UserDto;
import ru.tpu.oko.catalog.repo.KeyType;

import java.time.Instant;

/** Схема {@code ApiKeyIssued}: ключ целиком, показывается один раз. */
public record ApiKeyIssuedDto(long id, long productId, KeyType type, String keyPrefix, Instant createdAt,
                              UserDto createdBy, Instant revokedAt, String key) {
}
