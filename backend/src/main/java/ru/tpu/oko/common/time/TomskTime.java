package ru.tpu.oko.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** «День» в статистике — календарный день по Томску. */
public final class TomskTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tomsk");

    private TomskTime() {
    }

    public static LocalDate day(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }
}
