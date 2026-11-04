package ru.tpu.oko.common.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/** Настоящие часы приложения (в интеграционных тестах они подменены на MutableClock). */
class TimeConfigTest {

    private final Clock clock = new TimeConfig().clock();

    @Test
    void instantHasMicrosecondPrecisionAndMillisWork() {
        assertThat(clock.instant().getNano() % 1_000).isZero();
        assertThat(clock.millis()).isPositive();
        assertThat(Math.abs(clock.millis() - System.currentTimeMillis())).isLessThan(5_000);
    }
}
