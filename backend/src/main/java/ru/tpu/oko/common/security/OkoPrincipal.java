package ru.tpu.oko.common.security;

import java.io.Serializable;

/** Пользователь панели в сессии ОКО. Админство не хранится: каждый раз проверяется по конфигу. */
public record OkoPrincipal(long userId, String login) implements Serializable {
}
