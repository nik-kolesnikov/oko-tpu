package ru.tpu.oko.common.security;

import org.springframework.stereotype.Component;
import ru.tpu.oko.common.config.OkoProperties;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Админы ЦЭУ — список логинов из OKO_ADMIN_LOGINS и логин входа по паролю (OKO_ADMIN_LOGIN, если вход включён).
 * В БД не хранятся, из запроса не принимаются.
 */
@Component
public class AdminRegistry {

    private final Set<String> adminLogins;

    public AdminRegistry(OkoProperties properties) {
        Stream<String> passwordLogin = properties.passwordLogin().password().isEmpty()
                ? Stream.empty()
                : Stream.of(properties.passwordLogin().login());
        this.adminLogins = Stream.concat(properties.adminLogins().stream(), passwordLogin)
                .filter(login -> !login.isEmpty())
                .map(login -> login.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String login) {
        return login != null && adminLogins.contains(login.toLowerCase(Locale.ROOT));
    }
}
