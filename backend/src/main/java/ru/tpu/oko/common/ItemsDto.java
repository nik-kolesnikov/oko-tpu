package ru.tpu.oko.common;

import java.util.List;

/** Ответ-список без пагинации: {@code {"items": [...]}}. */
public record ItemsDto<T>(List<T> items) {
}
