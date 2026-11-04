package ru.tpu.oko.auth.api;

import ru.tpu.oko.auth.repo.AppUser;

/** Схема {@code User}. */
public record UserDto(long id, String login, String fullName, String email) {

    public static UserDto of(AppUser user) {
        return new UserDto(user.getId(), user.getLogin(), user.getFullName(), user.getEmail());
    }
}
