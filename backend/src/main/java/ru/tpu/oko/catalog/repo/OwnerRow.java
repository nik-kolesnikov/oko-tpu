package ru.tpu.oko.catalog.repo;

import ru.tpu.oko.auth.repo.AppUser;

/** Строка «владелец продукта» для пакетной загрузки владельцев. */
public record OwnerRow(Long productId, AppUser user) {
}
