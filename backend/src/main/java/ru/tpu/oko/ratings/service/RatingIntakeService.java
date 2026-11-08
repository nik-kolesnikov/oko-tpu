package ru.tpu.oko.ratings.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import ru.tpu.oko.catalog.repo.KeyType;
import ru.tpu.oko.catalog.repo.ProductStatus;
import ru.tpu.oko.catalog.repo.ResolvedKey;
import ru.tpu.oko.catalog.service.OriginFormat;
import ru.tpu.oko.catalog.service.PublicKeyService;
import ru.tpu.oko.common.config.OkoProperties;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorResponse.FieldErrorDto;
import ru.tpu.oko.common.ratelimit.RateLimiter;
import ru.tpu.oko.ratings.api.RaterRole;
import ru.tpu.oko.ratings.api.ServerRatingRequest;
import ru.tpu.oko.ratings.api.WidgetRatingRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Приём оценок. Порядок проверок — как в спецификации: ключ → продукт активен → Origin (только виджет) →
 * лимиты → формат и длина → serviceId принадлежит продукту → дубликат за 14 дней (в транзакции {@link RatingWriter}).
 */
@Service
public class RatingIntakeService {

    private final PublicKeyService keys;
    private final RateLimiter rateLimiter;
    private final Validator validator;
    private final RaterHasher raterHasher;
    private final RatingWriter writer;
    private final Clock clock;
    private final OkoProperties.RateLimits limits;
    private final String ownOrigin;

    public RatingIntakeService(PublicKeyService keys, RateLimiter rateLimiter, Validator validator,
                               RaterHasher raterHasher, RatingWriter writer, Clock clock, OkoProperties properties) {
        this.keys = keys;
        this.rateLimiter = rateLimiter;
        this.validator = validator;
        this.raterHasher = raterHasher;
        this.writer = writer;
        this.clock = clock;
        this.limits = properties.rateLimit();
        this.ownOrigin = OriginFormat.normalize(properties.publicOrigin()).orElse(properties.publicOrigin());
    }

    public long submitFromWidget(String apiKey, String origin, String clientIp, WidgetRatingRequest request) {
        ResolvedKey key = requireKey(apiKey, KeyType.PUBLIC);
        requireOrigin(origin);
        rateLimiter.check("widget-rating:key:" + key.keyId(), limits.widgetRatingPerKey());
        rateLimiter.check("rating:ip:" + clientIp, limits.ratingPerIp());
        validate(request);
        requireService(request.serviceId(), key.productId());
        return writer.write(new NewRating(key.productId(), request.serviceId(), request.score(),
                blankToNull(request.comment()), raterHasher.hash(request.raterId()), RaterRole.UNKNOWN.code(),
                blankToNull(request.pagePath()), blankToNull(request.version()), "widget", Instant.now(clock)));
    }

    /** Секретный ключ. Лимиты мягче: с одного IP-адреса сервера продукта идут оценки многих людей. */
    public long submitFromServer(String apiKey, String clientIp, ServerRatingRequest request) {
        ResolvedKey key = requireKey(apiKey, KeyType.SECRET);
        rateLimiter.check("server-rating:key:" + key.keyId(), limits.serverRatingPerKey());
        validate(request);
        requireService(request.serviceId(), key.productId());
        RaterRole role = request.raterRole() == null ? RaterRole.UNKNOWN : request.raterRole();
        return writer.write(new NewRating(key.productId(), request.serviceId(), request.score(),
                blankToNull(request.comment()), raterHasher.hash(request.raterId()), role.code(),
                blankToNull(request.pagePath()), blankToNull(request.version()), "api", Instant.now(clock)));
    }

    private ResolvedKey requireKey(String apiKey, KeyType expected) {
        ResolvedKey key = keys.resolve(apiKey)
                .filter(k -> k.type() == expected)
                .orElseThrow(ApiException::invalidKey);
        if (key.productStatus() != ProductStatus.ACTIVE) {
            throw ApiException.productInactive();
        }
        return key;
    }

    /**
     * Оценку шлёт только наш iframe, поэтому Origin — всегда origin ОКО. Сайты продукта проверяет браузер раньше,
     * по frame-ancestors на /widget/frame. Origin — не аутентификация, а лишь ещё один фильтр.
     */
    private void requireOrigin(String origin) {
        Optional<String> normalized = OriginFormat.normalize(origin);
        if (normalized.isEmpty() || !normalized.get().equals(ownOrigin)) {
            throw ApiException.originNotAllowed();
        }
    }

    private void requireService(Long serviceId, long productId) {
        if (serviceId != null && !keys.isActiveServiceOf(serviceId, productId)) {
            throw ApiException.validation("serviceId", "сервис не найден в этом продукте");
        }
    }

    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            List<FieldErrorDto> details = violations.stream()
                    .map(v -> new FieldErrorDto(v.getPropertyPath().toString(), v.getMessage()))
                    .sorted(Comparator.comparing(FieldErrorDto::field))
                    .toList();
            throw ApiException.validation(details);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
