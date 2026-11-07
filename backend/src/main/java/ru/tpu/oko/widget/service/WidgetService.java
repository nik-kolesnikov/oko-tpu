package ru.tpu.oko.widget.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import ru.tpu.oko.catalog.repo.KeyType;
import ru.tpu.oko.catalog.repo.ProductStatus;
import ru.tpu.oko.catalog.repo.ResolvedKey;
import ru.tpu.oko.catalog.service.PublicKeyService;
import ru.tpu.oko.common.config.OkoProperties;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.ratelimit.RateLimiter;
import ru.tpu.oko.widget.api.WidgetConfigDto;

import java.util.List;
import java.util.Optional;

@Service
public class WidgetService {

    /** Скрипт формы — зона фронтенда (frontend/widget), nginx отдаёт его статикой. */
    static final String FRAME_SCRIPT = "/widget/v1/frame.js";

    private final PublicKeyService keys;
    private final RateLimiter rateLimiter;
    private final OkoProperties.RateLimits limits;

    public WidgetService(PublicKeyService keys, RateLimiter rateLimiter, OkoProperties properties) {
        this.keys = keys;
        this.rateLimiter = rateLimiter;
        this.limits = properties.rateLimit();
    }

    /**
     * HTML iframe и значение CSP. Пусто — ключ неверный/отозван/не публичный, продукт не активен
     * или serviceId чужой: тогда 403 с пустым телом, виджет скрывается.
     */
    public Optional<Frame> frame(String apiKey, String serviceIdParam) {
        Optional<ResolvedKey> resolved = keys.resolve(apiKey)
                .filter(k -> k.type() == KeyType.PUBLIC && k.productStatus() == ProductStatus.ACTIVE);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        ResolvedKey key = resolved.get();
        Long serviceId = null;
        if (serviceIdParam != null && !serviceIdParam.isBlank()) {
            try {
                serviceId = Long.parseLong(serviceIdParam);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (!keys.isActiveServiceOf(serviceId, key.productId())) {
                return Optional.empty();
            }
        }
        String html = """
                <!doctype html>
                <html lang="ru">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="referrer" content="no-referrer">
                <title>ОКО</title>
                </head>
                <body>
                <div id="oko-root" data-key="%s" data-service-id="%s"></div>
                <script src="%s" defer></script>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(apiKey), serviceId == null ? "" : serviceId, FRAME_SCRIPT);
        return Optional.of(new Frame(html, contentSecurityPolicy(keys.allowedOrigins(key.productId()))));
    }

    public WidgetConfigDto config(String apiKey, String clientIp) {
        ResolvedKey key = keys.resolve(apiKey)
                .filter(k -> k.type() == KeyType.PUBLIC)
                .orElseThrow(ApiException::invalidKey);
        rateLimiter.check("widget-config:key:" + key.keyId(), limits.widgetConfigPerKey());
        rateLimiter.check("widget-config:ip:" + clientIp, limits.widgetConfigPerIp());
        if (key.productStatus() != ProductStatus.ACTIVE) {
            return WidgetConfigDto.DISABLED;
        }
        return keys.widgetSettings(key.productId())
                .filter(s -> s.isWidgetEnabled())
                .map(s -> new WidgetConfigDto(true, s.getQuestion(), s.getAskIntervalDays(), s.getCommentThreshold(),
                        s.getPosition()))
                .orElse(WidgetConfigDto.DISABLED);
    }

    /** frame-ancestors — по разрешённым сайтам продукта; пустой список запрещает встраивание везде. */
    static String contentSecurityPolicy(List<String> allowedOrigins) {
        String ancestors = allowedOrigins.isEmpty() ? "'none'" : String.join(" ", allowedOrigins);
        return "frame-ancestors " + ancestors + "; default-src 'none'; script-src 'self'; connect-src 'self'; "
                + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; base-uri 'none'; form-action 'none'";
    }

    public record Frame(String html, String contentSecurityPolicy) {
    }
}
