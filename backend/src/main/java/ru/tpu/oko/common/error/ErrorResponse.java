package ru.tpu.oko.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Схема {@code Error}: плоский объект, без обёртки {@code error}. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(ErrorCode code, String message, List<FieldErrorDto> details) {

    public record FieldErrorDto(String field, String message) {
    }
}
