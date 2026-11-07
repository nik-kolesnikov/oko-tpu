package ru.tpu.oko.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.catalog.repo.AllowedOriginRepository;
import ru.tpu.oko.catalog.repo.ApiKeyRepository;
import ru.tpu.oko.catalog.repo.ResolvedKey;
import ru.tpu.oko.catalog.repo.ServiceRepository;
import ru.tpu.oko.catalog.repo.ServiceState;
import ru.tpu.oko.catalog.repo.WidgetSettings;
import ru.tpu.oko.catalog.repo.WidgetSettingsRepository;
import ru.tpu.oko.common.crypto.Crypto;

import java.util.List;
import java.util.Optional;

/**
 * Каталог для публичных эндпоинтов (виджет, приём оценок): без сессии, авторизация по ключу.
 * Ключ проверяется по БД на каждом запросе — отозванный перестаёт работать сразу.
 */
@Service
@Transactional(readOnly = true)
public class PublicKeyService {

    private static final int MAX_KEY_LENGTH = 200;

    private final ApiKeyRepository keys;
    private final AllowedOriginRepository origins;
    private final ServiceRepository services;
    private final WidgetSettingsRepository widgetSettings;

    public PublicKeyService(ApiKeyRepository keys, AllowedOriginRepository origins, ServiceRepository services,
                            WidgetSettingsRepository widgetSettings) {
        this.keys = keys;
        this.origins = origins;
        this.services = services;
        this.widgetSettings = widgetSettings;
    }

    public Optional<ResolvedKey> resolve(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.length() > MAX_KEY_LENGTH) {
            return Optional.empty();
        }
        return keys.resolveActive(Crypto.sha256(rawKey));
    }

    public List<String> allowedOrigins(long productId) {
        return origins.findOrigins(productId);
    }

    public boolean isActiveServiceOf(long serviceId, long productId) {
        return services.existsByIdAndProductIdAndStatus(serviceId, productId, ServiceState.ACTIVE);
    }

    public Optional<WidgetSettings> widgetSettings(long productId) {
        return widgetSettings.findById(productId);
    }
}
