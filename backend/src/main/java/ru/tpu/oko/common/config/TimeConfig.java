package ru.tpu.oko.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Configuration
public class TimeConfig {

    /**
     * Все «сейчас» в коде берутся отсюда, чтобы тесты могли сдвигать время.
     * Точность — микросекунды, как у timestamptz: иначе ответ на создание и последующее чтение расходятся.
     */
    @Bean
    Clock clock() {
        return new MicrosecondClock();
    }

    /**
     * Не {@code Clock.tick(systemUTC(), 1 мкс)}: у такого Clock метод millis() делит на tick в миллисекундах
     * и падает с ArithmeticException при шаге меньше миллисекунды.
     */
    static final class MicrosecondClock extends Clock {

        private final Clock base = Clock.systemUTC();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return base.instant().truncatedTo(ChronoUnit.MICROS);
        }

        @Override
        public long millis() {
            return base.millis();
        }
    }
}
