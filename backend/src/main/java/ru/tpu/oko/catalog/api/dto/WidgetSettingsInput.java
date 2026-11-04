package ru.tpu.oko.catalog.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.tpu.oko.catalog.repo.WidgetPosition;

/** Схема {@code WidgetSettingsInput}: полная замена, все поля обязательны. */
public record WidgetSettingsInput(
        @NotNull(message = "обязательное поле") @Size(max = 200, message = "не длиннее 200 символов")
        String question,
        @NotNull(message = "обязательное поле") @Min(value = 1, message = "от 1 до 90") @Max(value = 90, message = "от 1 до 90")
        Integer askIntervalDays,
        @NotNull(message = "обязательное поле") @Min(value = 1, message = "от 1 до 5") @Max(value = 5, message = "от 1 до 5")
        Integer commentThreshold,
        @NotNull(message = "обязательное поле")
        WidgetPosition position,
        @NotNull(message = "обязательное поле")
        Boolean widgetEnabled) {
}
