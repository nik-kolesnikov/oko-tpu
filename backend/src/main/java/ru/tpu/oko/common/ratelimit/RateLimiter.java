package ru.tpu.oko.common.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.tpu.oko.common.error.ApiException;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Лимит запросов в минуту, фиксированное окно, в памяти приложения. Для MVP на одном сервере достаточно;
 * при нескольких экземплярах лимит нужно переносить в общее хранилище (Redis).
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Засчитывает запрос в корзину {@code bucket}; при превышении — 429 RATE_LIMITED с Retry-After. */
    public void check(String bucket, int limitPerMinute) {
        long now = clock.millis();
        long window = now / WINDOW_MILLIS;
        Window current = windows.compute(bucket, (key, old) ->
                old == null || old.window() != window ? new Window(window, 1) : new Window(window, old.count() + 1));
        if (current.count() > limitPerMinute) {
            long secondsLeft = ((window + 1) * WINDOW_MILLIS - now + 999) / 1000;
            throw ApiException.rateLimited((int) Math.max(1, secondsLeft));
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void evictExpired() {
        long window = clock.millis() / WINDOW_MILLIS;
        windows.values().removeIf(w -> w.window() < window);
    }

    int size() {
        return windows.size();
    }

    private record Window(long window, int count) {
    }
}
