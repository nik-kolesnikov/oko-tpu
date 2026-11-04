package ru.tpu.oko.catalog.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tpu.oko.catalog.api.dto.WidgetSettingsDto;
import ru.tpu.oko.catalog.api.dto.WidgetSettingsInput;
import ru.tpu.oko.catalog.service.WidgetSettingsService;
import ru.tpu.oko.common.ApiPaths;

@RestController
@RequestMapping(ApiPaths.V1 + "/products/{productId}/widget-settings")
public class WidgetSettingsController {

    private final WidgetSettingsService settings;

    public WidgetSettingsController(WidgetSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public WidgetSettingsDto get(@PathVariable long productId) {
        return settings.get(productId);
    }

    @PutMapping
    public WidgetSettingsDto update(@PathVariable long productId, @Valid @RequestBody WidgetSettingsInput request) {
        return settings.update(productId, request);
    }
}
