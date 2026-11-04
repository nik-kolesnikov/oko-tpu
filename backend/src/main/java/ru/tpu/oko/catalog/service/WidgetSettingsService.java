package ru.tpu.oko.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.catalog.api.dto.WidgetSettingsDto;
import ru.tpu.oko.catalog.api.dto.WidgetSettingsInput;
import ru.tpu.oko.catalog.repo.WidgetSettings;
import ru.tpu.oko.catalog.repo.WidgetSettingsRepository;

import java.time.Clock;
import java.time.Instant;

@Service
public class WidgetSettingsService {

    private final WidgetSettingsRepository settings;
    private final ProductAccess access;
    private final Clock clock;

    public WidgetSettingsService(WidgetSettingsRepository settings, ProductAccess access, Clock clock) {
        this.settings = settings;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WidgetSettingsDto get(long productId) {
        access.requireVisible(productId);
        return WidgetSettingsDto.of(settings.findById(productId)
                .orElseGet(() -> WidgetSettings.defaults(productId, Instant.now(clock))));
    }

    /** Полная замена. До виджета доходит при следующей загрузке страницы. */
    @Transactional
    public WidgetSettingsDto update(long productId, WidgetSettingsInput input) {
        access.requireVisible(productId);
        WidgetSettings s = load(productId);
        s.update(input.question(), input.askIntervalDays(), input.commentThreshold(), input.position(),
                input.widgetEnabled(), Instant.now(clock));
        return WidgetSettingsDto.of(settings.saveAndFlush(s));
    }

    private WidgetSettings load(long productId) {
        // Строка создаётся вместе с продуктом; если её нет (данные завели руками) — отдаём настройки по умолчанию.
        return settings.findById(productId)
                .orElseGet(() -> settings.save(WidgetSettings.defaults(productId, Instant.now(clock))));
    }
}
