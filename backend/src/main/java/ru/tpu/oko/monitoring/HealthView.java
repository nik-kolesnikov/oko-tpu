package ru.tpu.oko.monitoring;

import java.time.Instant;

/** Схема {@code Health}: лампочка сервиса. */
public record HealthView(HealthStatus status, Integer responseMs, Instant checkedAt) {

    public static final HealthView NO_DATA = new HealthView(HealthStatus.UNKNOWN, null, null);
}
