package ru.tpu.oko.monitoring;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ru.tpu.oko.common.config.OkoProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Читает последний известный статус из service_status. Сам мониторинг на запрос не дёргается. */
@Component
public class HealthReader {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration staleAfter;

    public HealthReader(JdbcClient jdbc, Clock clock, OkoProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.staleAfter = properties.monitoring().staleAfter();
    }

    /** Статус для каждого из {@code serviceIds}; если записи нет — «нет данных». */
    public Map<Long, HealthView> read(Collection<Long> serviceIds) {
        Map<Long, HealthView> result = new HashMap<>();
        if (serviceIds.isEmpty()) {
            return result;
        }
        Instant freshSince = clock.instant().minus(staleAfter);
        List<Row> rows = jdbc.sql("""
                        select service_id, status, response_ms, checked_at
                        from service_status where service_id in (:ids)
                        """)
                .param("ids", serviceIds)
                .query((rs, n) -> new Row(rs.getLong("service_id"), rs.getString("status"),
                        (Integer) rs.getObject("response_ms"), rs.getTimestamp("checked_at").toInstant()))
                .list();
        for (Row row : rows) {
            // Устаревшие данные — «нет данных», но время последней проверки сохраняем (см. схему HealthStatus).
            HealthView view = row.checkedAt().isBefore(freshSince)
                    ? new HealthView(HealthStatus.UNKNOWN, null, row.checkedAt())
                    : new HealthView(HealthStatus.fromCode(row.status()), row.responseMs(), row.checkedAt());
            result.put(row.serviceId(), view);
        }
        for (Long id : serviceIds) {
            result.putIfAbsent(id, HealthView.NO_DATA);
        }
        return result;
    }

    private record Row(long serviceId, String status, Integer responseMs, Instant checkedAt) {
    }
}
