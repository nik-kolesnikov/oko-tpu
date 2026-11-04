package ru.tpu.oko.catalog.service;

import ru.tpu.oko.common.error.ApiException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** URL продуктов и сервисов храним, но сами по ним не ходим (защита от SSRF). Проверяем только формат. */
final class UrlFormat {

    static final int MAX_LENGTH = 2000;

    private UrlFormat() {
    }

    static String requireHttpUrl(String field, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw ApiException.validation(field, "обязательное поле");
        }
        if (value.length() > MAX_LENGTH) {
            throw ApiException.validation(field, "не длиннее " + MAX_LENGTH + " символов");
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!uri.isAbsolute() || !(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw ApiException.validation(field, "нужен абсолютный адрес http:// или https://");
            }
        } catch (URISyntaxException e) {
            throw ApiException.validation(field, "некорректный адрес");
        }
        return value;
    }

    /** Для необязательных полей: null и пустая строка — «нет значения». */
    static String optionalHttpUrl(String field, String raw) {
        return raw == null || raw.isBlank() ? null : requireHttpUrl(field, raw);
    }
}
