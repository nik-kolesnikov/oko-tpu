package ru.tpu.oko.monitoring.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Wire format of the monitoring team's push contract. */
public record StatusEvent(
        @JsonProperty(value = "event_id", required = true) @NotNull UUID eventId,
        @JsonProperty(value = "event_type", required = true) @NotNull EventType eventType,
        @JsonProperty(value = "resource_id", required = true) @NotBlank @Size(max = 255) String resourceId,
        @JsonProperty(value = "resource_name", required = true) @NotBlank @Size(max = 500) String resourceName,
        @JsonProperty(value = "status", required = true) @NotNull Status status,
        @JsonProperty(value = "check_type", required = true) @NotNull CheckType checkType,
        @JsonProperty(value = "checked_at", required = true) @NotNull Instant checkedAt,
        @JsonProperty(value = "response_time_ms", required = true) @PositiveOrZero BigDecimal responseTimeMs,
        @JsonProperty(value = "error", required = true) @Size(max = 4096) String error) {

    public enum EventType { STATUS_CHANGED, HEARTBEAT }
    public enum Status { UNKNOWN, UP, DOWN }
    public enum CheckType { HTTP, TCP }
}
