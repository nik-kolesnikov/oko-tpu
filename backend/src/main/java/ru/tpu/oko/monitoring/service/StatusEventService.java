package ru.tpu.oko.monitoring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.monitoring.api.StatusEvent;

import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Durable, transactional receipt of at-least-once push events. */
@Service
public class StatusEventService {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public StatusEventService(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Returns true for a new event, false for the same event delivered again. */
    @Transactional
    public boolean receive(StatusEvent event) {
        responseMs(event);
        String payload = json(event);
        int inserted = jdbc.sql("""
                        insert into monitoring_event (event_id, resource_id, payload, checked_at, event_type)
                        values (:id, :ref, cast(:payload as jsonb), :checked, :type)
                        on conflict (event_id) do nothing
                        """)
                .param("id", event.eventId()).param("ref", event.resourceId())
                .param("payload", payload).param("checked", Timestamp.from(event.checkedAt()))
                .param("type", event.eventType().name()).update();
        if (inserted == 0) {
            boolean same = jdbc.sql("""
                            select payload = cast(:payload as jsonb) from monitoring_event where event_id = :id
                            """)
                    .param("payload", payload).param("id", event.eventId())
                    .query(Boolean.class).single();
            if (!same) {
                throw ApiException.conflict(ErrorCode.CONFLICT, "event_id уже использован с другим содержимым");
            }
            bindIfMapped(event.resourceId());
            return false;
        }
        bindIfMapped(event.resourceId());
        return true;
    }

    private void bindIfMapped(String resourceId) {
        Long serviceId = jdbc.sql("select id from service where monitoring_ref = :ref for update")
                .param("ref", resourceId).query(Long.class).optional().orElse(null);
        if (serviceId != null) {
            // Also catches a concurrent registration that could not see a just-arrived event.
            bindPending(serviceId, resourceId);
        }
    }

    /** Bind events received before the matching service was registered. Called in the service transaction. */
    public void bindPending(long serviceId, String resourceId) {
        if (resourceId == null) {
            return;
        }
        List<Pending> pending = jdbc.sql("""
                        select event_id, payload::text from monitoring_event
                        where resource_id = :ref and service_id is null
                        order by checked_at, case event_type when 'HEARTBEAT' then 0 else 1 end, event_id
                        """)
                .param("ref", resourceId)
                .query((rs, n) -> new Pending(rs.getObject(1, java.util.UUID.class), rs.getString(2))).list();
        for (Pending item : pending) {
            StatusEvent event;
            try {
                event = mapper.readValue(item.payload(), StatusEvent.class);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Stored monitoring event is unreadable", ex);
            }
            apply(serviceId, event, responseMs(event));
            jdbc.sql("update monitoring_event set service_id = :service where event_id = :id")
                    .param("service", serviceId).param("id", item.id()).update();
        }
    }

    private void apply(long serviceId, StatusEvent event, int responseMs) {
        Current current = jdbc.sql("""
                        select status, checked_at, event_type from service_status where service_id = :id
                        """)
                .param("id", serviceId)
                .query((rs, n) -> new Current(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getString(3)))
                .optional().orElse(null);
        boolean newer = current == null || event.checkedAt().isAfter(current.checkedAt())
                || (event.checkedAt().equals(current.checkedAt())
                    && event.eventType() == StatusEvent.EventType.STATUS_CHANGED
                    && !"STATUS_CHANGED".equals(current.eventType()));
        if (!newer) {
            return;
        }
        String status = event.status().name().toLowerCase(java.util.Locale.ROOT);
        if (current == null) {
            jdbc.sql("""
                            insert into service_status (service_id, status, response_ms, checked_at, event_type)
                            values (:id, :status, :ms, :checked, :type)
                            """)
                    .param("id", serviceId).param("status", status)
                    .param("ms", event.responseTimeMs() == null ? null : responseMs)
                    .param("checked", Timestamp.from(event.checkedAt()))
                    .param("type", event.eventType().name()).update();
        } else {
            jdbc.sql("""
                            update service_status set status = :status, response_ms = :ms,
                                checked_at = :checked, event_type = :type, received_at = now()
                            where service_id = :id
                            """)
                    .param("id", serviceId).param("status", status)
                    .param("ms", event.responseTimeMs() == null ? null : responseMs)
                    .param("checked", Timestamp.from(event.checkedAt()))
                    .param("type", event.eventType().name()).update();
        }
        if (current == null || !status.equals(current.status())) {
            jdbc.sql("""
                            insert into service_status_log (service_id, status, response_ms, checked_at)
                            values (:id, :status, :ms, :checked)
                            """)
                    .param("id", serviceId).param("status", status)
                    .param("ms", event.responseTimeMs() == null ? null : responseMs)
                    .param("checked", Timestamp.from(event.checkedAt())).update();
        }
    }

    private static int responseMs(StatusEvent event) {
        if (event.responseTimeMs() == null) {
            return 0;
        }
        try {
            return event.responseTimeMs().setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException ex) {
            throw ApiException.validation("response_time_ms", "значение слишком велико");
        }
    }

    private String json(StatusEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize monitoring event", ex);
        }
    }

    private record Current(String status, Instant checkedAt, String eventType) { }
    private record Pending(java.util.UUID id, String payload) { }
}
