package ru.tpu.oko.common.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TomskTimeTest {

    @Test
    void dayChangesAtTomskMidnightNotUtc() {
        // Томск — UTC+7: полночь по Томску — 17:00 UTC предыдущего дня.
        assertThat(TomskTime.day(Instant.parse("2026-11-04T16:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(TomskTime.day(Instant.parse("2026-11-04T17:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 5));
    }
}
