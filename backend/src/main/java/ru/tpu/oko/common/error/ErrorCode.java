package ru.tpu.oko.common.error;

/** Коды ошибок — ровно enum схемы {@code Error.code} из contracts/oko-openapi.yaml. Новый код — только через PR в спецификацию. */
public enum ErrorCode {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    VALIDATION_ERROR,
    CONFLICT,
    DUPLICATE_URL,
    DUPLICATE_MONITORING_REF,
    LAST_OWNER,
    INVALID_KEY,
    ORIGIN_NOT_ALLOWED,
    PRODUCT_INACTIVE,
    DUPLICATE_RATING,
    RATE_LIMITED,
    UNAVAILABLE
}
