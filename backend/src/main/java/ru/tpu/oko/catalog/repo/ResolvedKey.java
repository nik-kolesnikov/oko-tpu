package ru.tpu.oko.catalog.repo;

/** Действующий ключ, найденный по X-Api-Key. */
public record ResolvedKey(Long keyId, Long productId, KeyType type, ProductStatus productStatus) {
}
