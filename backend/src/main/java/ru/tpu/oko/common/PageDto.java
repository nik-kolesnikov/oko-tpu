package ru.tpu.oko.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Схема {@code PageMeta} + items. Страницы нумеруются с 1. */
public record PageDto<T>(int page, int size, long totalItems, int totalPages, List<T> items) {

    public static <E, T> PageDto<T> of(Page<E> page, Function<List<E>, List<T>> mapper) {
        return new PageDto<>(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages(),
                mapper.apply(page.getContent()));
    }
}
