package ru.tpu.oko.common.security;

import org.springframework.stereotype.Component;
import ru.tpu.oko.common.config.OkoProperties;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Админы ЦЭУ — список логинов из OKO_ADMIN_LOGINS. В БД не хранятся, из запроса не принимаются. */
@Component
public class AdminRegistry {

    private final Set<String> adminLogins;

    public AdminRegistry(OkoProperties properties) {
        this.adminLogins = properties.adminLogins().stream()
                .map(login -> login.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String login) {
        return login != null && adminLogins.contains(login.toLowerCase(Locale.ROOT));
    }
}
