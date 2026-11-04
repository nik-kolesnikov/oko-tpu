package ru.tpu.oko.monitoring;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Заглушка до появления API мониторинга: данных нет, все лампочки «нет данных». */
@Component
public class StubMonitoringSource implements MonitoringSource {

    @Override
    public List<Sample> fetch(Collection<String> monitoringRefs) {
        return List.of();
    }
}
