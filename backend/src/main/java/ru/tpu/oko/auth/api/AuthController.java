package ru.tpu.oko.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import ru.tpu.oko.auth.service.AdminPassword;
import ru.tpu.oko.auth.service.ExternalIdentity;
import ru.tpu.oko.auth.service.IdentityException;
import ru.tpu.oko.auth.service.IdentityProvider;
import ru.tpu.oko.auth.service.LoginService;
import ru.tpu.oko.auth.service.ReturnTo;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.common.config.OkoProperties;
import ru.tpu.oko.common.crypto.Crypto;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.security.CurrentUser;
import ru.tpu.oko.common.security.OkoPrincipal;

import java.net.URI;

@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    static final String STATE_ATTR = "oko.auth.state";
    static final String RETURN_TO_ATTR = "oko.auth.returnTo";

    private final ObjectProvider<IdentityProvider> identityProvider;
    private final AdminPassword adminPassword;
    private final LoginService loginService;
    private final CurrentUser currentUser;
    private final SessionSignIn sessionSignIn;
    private final URI callbackUri;

    public AuthController(ObjectProvider<IdentityProvider> identityProvider, AdminPassword adminPassword,
                          LoginService loginService, CurrentUser currentUser, SessionSignIn sessionSignIn,
                          OkoProperties properties) {
        this.identityProvider = identityProvider;
        this.adminPassword = adminPassword;
        this.loginService = loginService;
        this.currentUser = currentUser;
        this.sessionSignIn = sessionSignIn;
        this.callbackUri = URI.create(properties.publicOrigin() + ApiPaths.V1 + "/auth/callback");
        if (identityProvider.getIfAvailable() == null) {
            if (adminPassword.enabled()) {
                log.warn("IdentityProvider не настроен: вход в панель — только админ по паролю (OKO_ADMIN_LOGIN)");
            } else {
                log.warn("IdentityProvider не настроен: вход в панель отвечает 503. SSO ТПУ ещё не подключён, "
                        + "заглушка доступна только в профиле dev, вход админа по паролю выключен");
            }
        }
    }

    /**
     * Панель всегда начинает вход здесь. Есть SSO — уходим в SSO (в dev — на заглушку). Нет SSO, но включён вход
     * админа по паролю — на его страницу. Нет ни того, ни другого — 503.
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login(@RequestParam(required = false) String returnTo, HttpServletRequest request) {
        IdentityProvider provider = identityProvider.getIfAvailable();
        if (provider == null && adminPassword.enabled()) {
            return redirect(UriComponentsBuilder.fromPath(PasswordLoginController.PATH)
                    .queryParam("returnTo", ReturnTo.sanitize(returnTo))
                    .encode()
                    .build()
                    .toUri());
        }
        if (provider == null) {
            throw ApiException.unavailable("Вход через SSO ТПУ ещё не подключён");
        }
        String state = Crypto.randomToken(32);
        HttpSession session = request.getSession(true);
        session.setAttribute(STATE_ATTR, state);
        session.setAttribute(RETURN_TO_ATTR, ReturnTo.sanitize(returnTo));
        return redirect(provider.authorizationUri(state, callbackUri));
    }

    /** Запрос с code в логи не пишем целиком: при обмене code уходит client_secret. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state,
                                         HttpServletRequest request, HttpServletResponse response) {
        IdentityProvider provider = requireProvider();
        HttpSession session = request.getSession(false);
        String expectedState = session == null ? null : (String) session.getAttribute(STATE_ATTR);
        if (!Crypto.constantTimeEquals(expectedState, state)) {
            throw ApiException.validation("state", "не совпадает с выданным при входе, начните вход заново");
        }
        String returnTo = (String) session.getAttribute(RETURN_TO_ATTR);
        session.removeAttribute(STATE_ATTR);
        session.removeAttribute(RETURN_TO_ATTR);

        ExternalIdentity identity;
        try {
            identity = provider.exchange(code, callbackUri);
        } catch (IdentityException e) {
            throw ApiException.unauthorized("Вход не подтверждён, попробуйте ещё раз");
        } catch (RuntimeException e) {
            log.error("SSO недоступен при обмене code: {}", e.getClass().getSimpleName());
            throw ApiException.unavailable("Сервис входа временно недоступен");
        }
        OkoPrincipal principal = loginService.upsert(identity);
        sessionSignIn.signIn(principal, request, response);

        return redirect(URI.create(ReturnTo.sanitize(returnTo)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserDto me() {
        return loginService.current(currentUser.id());
    }

    private IdentityProvider requireProvider() {
        IdentityProvider provider = identityProvider.getIfAvailable();
        if (provider == null) {
            throw ApiException.unavailable("Вход через SSO ТПУ ещё не подключён");
        }
        return provider;
    }

    private static ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location.toString()).build();
    }
}
