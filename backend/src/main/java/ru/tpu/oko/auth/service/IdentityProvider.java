package ru.tpu.oko.auth.service;

import java.net.URI;

/**
 * Источник личности для входа в панель. Сейчас — заглушка (профиль dev), после выдачи доступа — SSO ТПУ
 * (oauth.tpu.ru, authorization code). Остальной код от реализации не зависит.
 */
public interface IdentityProvider {

    /** Куда отправить браузер для входа. {@code state} вернётся в callback и там проверяется. */
    URI authorizationUri(String state, URI callbackUri);

    /**
     * Обменивает {@code code} на данные пользователя. Запрос обмена содержит client_secret — его не логируем.
     *
     * @throws IdentityException code неверный или просрочен
     */
    ExternalIdentity exchange(String code, URI callbackUri);
}
