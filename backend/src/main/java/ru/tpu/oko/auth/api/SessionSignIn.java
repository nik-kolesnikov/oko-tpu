package ru.tpu.oko.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import ru.tpu.oko.common.security.OkoPrincipal;

import java.util.List;

/** Открывает сессию панели после успешного входа — одинаково для SSO и для входа админа по паролю. */
@Component
class SessionSignIn {

    private final SecurityContextRepository securityContextRepository;

    SessionSignIn(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    void signIn(OkoPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        // Новый идентификатор сессии после входа — защита от фиксации сессии.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
