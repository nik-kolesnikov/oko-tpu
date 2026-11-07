package ru.tpu.oko.catalog.api.dto;

import ru.tpu.oko.catalog.repo.ServiceState;
import ru.tpu.oko.monitoring.HealthView;

import java.time.Instant;

/** Схема {@code Service}. */
public record ServiceDto(long id, long productId, String name, String url, String monitoringRef, ServiceState status,
                         HealthView health, Instant createdAt) {
}
