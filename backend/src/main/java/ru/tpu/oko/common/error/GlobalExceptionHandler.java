package ru.tpu.oko.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.tpu.oko.common.error.ErrorResponse.FieldErrorDto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Единая точка превращения исключений в {@link ErrorResponse}. Стектрейсы и SQL наружу не уходят.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.status());
        if (ex.retryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        }
        return builder.body(ex.toResponse());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDto> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorDto(e.getField(), e.getDefaultMessage()))
                .toList();
        return validation(details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDto> details = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                details.add(new FieldErrorDto(result.getMethodParameter().getParameterName(), error.getDefaultMessage()))));
        return validation(details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDto> details = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDto(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validation(details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String field = mapping.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."))
                    .replace(".[", "[");
            return validation(List.of(new FieldErrorDto(field, "недопустимое значение или тип")));
        }
        return validation(List.of(new FieldErrorDto("body", "тело запроса не читается как JSON или слишком большое")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validation(List.of(new FieldErrorDto(ex.getName(), "недопустимое значение")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return validation(List.of(new FieldErrorDto(ex.getParameterName(), "обязательный параметр")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return validation(List.of(new FieldErrorDto(ex.getHeaderName(), "обязательный заголовок")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return validation(List.of(new FieldErrorDto("Content-Type", "ожидается application/json")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return handleApi(ApiException.notFound());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        // В enum спецификации нет отдельного кода для 405, ближайший по смыслу — NOT_FOUND.
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(ErrorCode.NOT_FOUND, "Метод не поддерживается", List.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        String constraint = constraintName(ex);
        if (constraint.contains("product_url_uq")) {
            return handleApi(ApiException.conflict(ErrorCode.DUPLICATE_URL, "Продукт с таким URL уже зарегистрирован"));
        }
        if (constraint.contains("service_monitoring_uq")) {
            return handleApi(ApiException.conflict(ErrorCode.DUPLICATE_MONITORING_REF,
                    "Этот идентификатор мониторинга уже привязан к другому сервису"));
        }
        log.warn("Нарушение ограничения БД: {}", constraint);
        return handleApi(ApiException.conflict(ErrorCode.CONFLICT, "Конфликт данных"));
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class})
    ResponseEntity<ErrorResponse> handleDbUnavailable(Exception ex) {
        log.error("База данных недоступна: {}", ex.getClass().getSimpleName());
        return handleApi(ApiException.unavailable("Сервис временно недоступен"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Необработанная ошибка", ex);
        // В enum спецификации нет кода для 500 (см. docs/team/Расхождения в документах.md).
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorCode.UNAVAILABLE, "Внутренняя ошибка сервера", List.of()));
    }

    private static ResponseEntity<ErrorResponse> validation(List<FieldErrorDto> details) {
        return ResponseEntity.badRequest().body(ApiException.validation(details).toResponse());
    }

    private static String constraintName(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
        }
        String message = ex.getMostSpecificCause().getMessage();
        return message == null ? "" : message;
    }
}
