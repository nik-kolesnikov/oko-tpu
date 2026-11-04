package ru.tpu.oko.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class TimeConfig {

    /**
     * Все «сейчас» в коде берутся отсюда, чтобы тесты могли сдвигать время.
     * Точность — микросекунды, как у timestamptz: иначе ответ на создание и последующее чтение расходятся.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
