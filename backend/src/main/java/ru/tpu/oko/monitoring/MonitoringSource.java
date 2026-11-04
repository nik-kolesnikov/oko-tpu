package ru.tpu.oko.monitoring;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * API команды мониторинга. Реальный адаптер появится, когда они отдадут API (около 10 ноября);
 * до этого — {@link StubMonitoringSource}, лампочки серые.
 * Опрос — фоновой задачей, панель и виджет читают только нашу таблицу service_status.
 */
public interface MonitoringSource {

    /** Последние проверки по идентификаторам их системы. Неизвестные идентификаторы в ответ не попадают. */
    List<Sample> fetch(Collection<String> monitoringRefs);

    record Sample(String monitoringRef, HealthStatus status, Integer responseMs, Instant checkedAt) {
    }
}
