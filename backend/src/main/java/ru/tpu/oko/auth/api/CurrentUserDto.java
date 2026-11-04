package ru.tpu.oko.auth.api;

/** Схема {@code CurrentUser}. */
public record CurrentUserDto(long id, String login, String fullName, String email, boolean isAdmin) {
}
