package ru.tpu.oko.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.tpu.oko.common.error.ApiException;

@Component
public class CurrentUser {

    private final AdminRegistry adminRegistry;

    public CurrentUser(AdminRegistry adminRegistry) {
        this.adminRegistry = adminRegistry;
    }

    public OkoPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OkoPrincipal principal) {
            return principal;
        }
        throw ApiException.unauthorized("Требуется вход");
    }

    public long id() {
        return require().userId();
    }

    public boolean isAdmin() {
        return adminRegistry.isAdmin(require().login());
    }
}
