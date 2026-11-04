package ru.tpu.oko.widget.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import ru.tpu.oko.catalog.repo.WidgetPosition;

/** Схема {@code WidgetConfig}. При enabled=false остальных полей нет. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WidgetConfigDto(boolean enabled, String question, Integer askIntervalDays, Integer commentThreshold,
                              WidgetPosition position) {

    public static final WidgetConfigDto DISABLED = new WidgetConfigDto(false, null, null, null, null);
}
