package ru.tpu.oko.auth.stub;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import ru.tpu.oko.auth.service.ExternalIdentity;
import ru.tpu.oko.auth.service.IdentityException;
import ru.tpu.oko.auth.service.IdentityProvider;
import ru.tpu.oko.common.ApiPaths;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Заглушка SSO для разработки: «code» — это просто логин, введённый на странице /auth/dev-login.
 * Только профиль dev; в prod её быть не должно (это проверяет тест).
 */
@Component
@Profile("dev")
public class StubIdentityProvider implements IdentityProvider {

    static final Pattern LOGIN = Pattern.compile("[a-z0-9._-]{2,64}");

    @Override
    public URI authorizationUri(String state, URI callbackUri) {
        return UriComponentsBuilder.fromPath(ApiPaths.V1 + "/auth/dev-login")
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    @Override
    public ExternalIdentity exchange(String code, URI callbackUri) {
        if (code == null || !LOGIN.matcher(code).matches()) {
            throw new IdentityException("Логин заглушки: латиница в нижнем регистре, цифры, . _ -");
        }
        return new ExternalIdentity(code, "Тестовый пользователь " + code, code + "@example.invalid");
    }
}
