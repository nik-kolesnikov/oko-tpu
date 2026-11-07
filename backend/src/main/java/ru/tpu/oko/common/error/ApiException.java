package ru.tpu.oko.common.error;

import org.springframework.http.HttpStatus;
import ru.tpu.oko.common.error.ErrorResponse.FieldErrorDto;

import java.util.List;

/** Ошибка, которая уходит клиенту как {@link ErrorResponse}. Сообщение — по-русски, без персональных данных. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final List<FieldErrorDto> details;
    private final Integer retryAfterSeconds;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, List.of(), null);
    }

    private ApiException(HttpStatus status, ErrorCode code, String message, List<FieldErrorDto> details,
                         Integer retryAfterSeconds) {
        super(message, null, false, false);
        this.status = status;
        this.code = code;
        this.details = details;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Не найдено");
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, message);
    }

    public static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException validation(String field, String message) {
        return validation(List.of(new FieldErrorDto(field, message)));
    }

    public static ApiException validation(List<FieldErrorDto> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Некорректные данные", details, null);
    }

    public static ApiException invalidKey() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_KEY,
                "Ключ не передан, неизвестен, отозван или не того типа");
    }

    public static ApiException productInactive() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.PRODUCT_INACTIVE,
                "Продукт приостановлен или в архиве, оценки не принимаются");
    }

    public static ApiException originNotAllowed() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ORIGIN_NOT_ALLOWED, "Сайт не входит в список разрешённых");
    }

    public static ApiException rateLimited(int retryAfterSeconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                "Слишком много запросов, повторите позже", List.of(), retryAfterSeconds);
    }

    public static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.UNAVAILABLE, message);
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldErrorDto> details() {
        return details;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public ErrorResponse toResponse() {
        return new ErrorResponse(code, getMessage(), details);
    }
}
