package ru.tpu.oko.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Часы, которые тест может сдвигать: проверка окна дубликатов, дня по Томску. */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now = new AtomicReference<>();

    public MutableClock() {
        reset();
    }

    public void reset() {
        now.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    public void set(Instant instant) {
        now.set(instant);
    }

    public void advance(Duration duration) {
        now.updateAndGet(i -> i.plus(duration));
    }

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
        return now.get();
    }
}
