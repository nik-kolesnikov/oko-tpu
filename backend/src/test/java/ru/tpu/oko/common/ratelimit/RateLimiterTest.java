package ru.tpu.oko.common.ratelimit;

import org.junit.jupiter.api.Test;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.support.MutableClock;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsUpToLimitWithinMinuteThenRejectsWithRetryAfter() {
        clock.set(Instant.parse("2026-11-05T10:00:15Z"));
        for (int i = 0; i < 3; i++) {
            limiter.check("key:1", 3);
        }
        assertThatThrownBy(() -> limiter.check("key:1", 3))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(e.retryAfterSeconds()).isEqualTo(45);
                });
        limiter.check("key:2", 3);
    }

    @Test
    void newMinuteStartsNewWindow() {
        clock.set(Instant.parse("2026-11-05T10:00:59Z"));
        limiter.check("ip:1", 1);
        clock.advance(Duration.ofSeconds(1));
        limiter.check("ip:1", 1);
    }

    @Test
    void expiredWindowsAreEvicted() {
        clock.set(Instant.parse("2026-11-05T10:00:00Z"));
        limiter.check("a", 10);
        limiter.check("b", 10);
        clock.advance(Duration.ofMinutes(2));
        limiter.evictExpired();
        assertThat(limiter.size()).isZero();
    }
}
