package ru.tpu.oko.catalog.api.dto;

import ru.tpu.oko.catalog.repo.WidgetPosition;
import ru.tpu.oko.catalog.repo.WidgetSettings;

import java.time.Instant;

/** Схема {@code WidgetSettings}. */
public record WidgetSettingsDto(String question, int askIntervalDays, int commentThreshold, WidgetPosition position,
                                boolean widgetEnabled, Instant updatedAt) {

    public static WidgetSettingsDto of(WidgetSettings s) {
        return new WidgetSettingsDto(s.getQuestion(), s.getAskIntervalDays(), s.getCommentThreshold(), s.getPosition(),
                s.isWidgetEnabled(), s.getUpdatedAt());
    }
}
