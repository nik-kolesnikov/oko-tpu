package ru.tpu.oko.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Настройки ОКО. Секреты приходят только из переменных окружения (см. deploy/.env.example).
 */
@Validated
@ConfigurationProperties("oko")
public record OkoProperties(
        /** Origin, с которого отдаются панель и iframe виджета, например https://oko.tpu.ru. */
        @NotBlank String publicOrigin,
        /** Логины ТПУ админов ЦЭУ. Пустой список — админов нет. */
        List<String> adminLogins,
        /** Секрет HMAC для raterId. Смена делает старые хэши несравнимыми. */
        @NotBlank @Size(min = 32, message = "должен быть не короче 32 символов") String raterHmacSecret,
        /** Окно «одна оценка от человека на продукт». */
        @DefaultValue("14d") Duration dedupWindow,
        @Valid @DefaultValue Monitoring monitoring,
        @Valid @DefaultValue RateLimits rateLimit) {

    public OkoProperties {
        adminLogins = adminLogins == null ? List.of() : adminLogins.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public record Monitoring(
            /** Статус старше порога показывается как «нет данных». */
            @DefaultValue("5m") Duration staleAfter) {
    }

    /** Лимиты запросов в минуту. Лимит по IP мягкий: в общежитиях много людей за одним адресом. */
    public record RateLimits(
            @DefaultValue("300") int widgetRatingPerKey,
            @DefaultValue("1200") int serverRatingPerKey,
            @DefaultValue("60") int ratingPerIp,
            @DefaultValue("1200") int widgetConfigPerKey,
            @DefaultValue("300") int widgetConfigPerIp) {
    }
}
