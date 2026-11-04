package ru.tpu.oko.auth.service;

/** Что знаем о пользователе панели после входа. lichnost_id для панели не нужен и не хранится. */
public record ExternalIdentity(String login, String fullName, String email) {
}
