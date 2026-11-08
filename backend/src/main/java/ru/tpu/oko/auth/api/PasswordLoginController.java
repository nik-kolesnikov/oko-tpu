package ru.tpu.oko.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import ru.tpu.oko.auth.service.AdminPassword;
import ru.tpu.oko.auth.service.ExternalIdentity;
import ru.tpu.oko.auth.service.LoginService;
import ru.tpu.oko.auth.service.ReturnTo;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.common.config.OkoProperties;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.common.ratelimit.RateLimiter;
import ru.tpu.oko.common.security.OkoPrincipal;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Вход админа по паролю, пока SSO ТПУ не подключён (см. {@link AdminPassword}). Серверная HTML-форма, а не экран SPA:
 * панель по-прежнему начинает вход с /auth/login, а backend сам решает, куда отправить.
 *
 * <p>Логин и пароль не логируются. На неверный вход — 401 без уточнения, что именно неверно. Лимит попыток — с одного
 * IP и всего (oko.rate-limit.password-login-*). CSRF проверяет Spring Security (скрытое поле _csrf).
 */
@RestController
public class PasswordLoginController {

    static final String PATH = ApiPaths.V1 + "/auth/password-login";

    private static final Logger log = LoggerFactory.getLogger(PasswordLoginController.class);
    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    /** Страница без скриптов и встраивания; форма уходит только на свой origin. */
    private static final String CSP = "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; "
            + "frame-ancestors 'none'; base-uri 'none'";
    private static final String DISPLAY_NAME = "Администратор ОКО";

    private final AdminPassword adminPassword;
    private final LoginService loginService;
    private final SessionSignIn sessionSignIn;
    private final RateLimiter rateLimiter;
    private final OkoProperties.RateLimits limits;

    public PasswordLoginController(AdminPassword adminPassword, LoginService loginService, SessionSignIn sessionSignIn,
                                   RateLimiter rateLimiter, OkoProperties properties) {
        this.adminPassword = adminPassword;
        this.loginService = loginService;
        this.sessionSignIn = sessionSignIn;
        this.rateLimiter = rateLimiter;
        this.limits = properties.rateLimit();
    }

    @GetMapping(PATH)
    public ResponseEntity<String> page(@RequestParam(required = false) String returnTo, HttpServletRequest request) {
        requireEnabled();
        return html(HttpStatus.OK, form(request, ReturnTo.sanitize(returnTo), null)).build();
    }

    @PostMapping(PATH)
    public ResponseEntity<String> login(@RequestParam(defaultValue = "") String login,
                                        @RequestParam(defaultValue = "") String password,
                                        @RequestParam(required = false) String returnTo,
                                        HttpServletRequest request, HttpServletResponse response) {
        requireEnabled();
        String safeReturnTo = ReturnTo.sanitize(returnTo);
        try {
            rateLimiter.check("password-login:ip:" + request.getRemoteAddr(), limits.passwordLoginPerIp());
            rateLimiter.check("password-login:all", limits.passwordLoginTotal());
        } catch (ApiException e) {
            if (e.code() != ErrorCode.RATE_LIMITED) {
                throw e;
            }
            log.warn("Вход админа по паролю: превышен лимит попыток");
            return html(HttpStatus.TOO_MANY_REQUESTS,
                    form(request, safeReturnTo, "Слишком много попыток. Повторите через минуту."))
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                    .build();
        }

        if (!adminPassword.matches(login, password)) {
            log.warn("Вход админа по паролю: неверный логин или пароль");
            return html(HttpStatus.UNAUTHORIZED, form(request, safeReturnTo, "Неверный логин или пароль.")).build();
        }

        String adminLogin = adminPassword.login().orElseThrow();
        OkoPrincipal principal = loginService.upsert(new ExternalIdentity(adminLogin, DISPLAY_NAME, null));
        sessionSignIn.signIn(principal, request, response);
        log.info("Вход админа по паролю выполнен");
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(safeReturnTo).toString())
                .build();
    }

    private void requireEnabled() {
        if (!adminPassword.enabled()) {
            throw ApiException.notFound();
        }
    }

    /** Тело и заголовки HTML-ответа; вызывающий добавляет свои заголовки и вызывает build(). */
    private static HtmlResponse html(HttpStatus status, String body) {
        return new HtmlResponse(status, body);
    }

    private static String form(HttpServletRequest request, String returnTo, String error) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String csrfField = csrf == null ? "" : """
                <input type="hidden" name="%s" value="%s">""".formatted(
                HtmlUtils.htmlEscape(csrf.getParameterName()), HtmlUtils.htmlEscape(csrf.getToken()));
        String errorBlock = error == null ? "" : """
                <p role="alert" style="color: #b42318">%s</p>""".formatted(HtmlUtils.htmlEscape(error));
        return """
                <!doctype html>
                <html lang="ru">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="robots" content="noindex">
                  <title>ОКО — вход администратора</title>
                </head>
                <body style="font-family: system-ui, sans-serif; max-width: 380px; margin: 64px auto; padding: 0 16px">
                  <h1 style="font-size: 1.4rem">Вход администратора ОКО</h1>
                  <p style="color: #555">Временный вход, пока не подключена единая авторизация ТПУ.</p>
                  %s
                  <form method="post" action="%s">
                    %s
                    <input type="hidden" name="returnTo" value="%s">
                    <p><label>Логин<br><input name="login" required autocomplete="username" autofocus
                      style="width: 100%%; padding: 6px"></label></p>
                    <p><label>Пароль<br><input name="password" type="password" required autocomplete="current-password"
                      style="width: 100%%; padding: 6px"></label></p>
                    <p><button type="submit" style="padding: 6px 16px">Войти</button></p>
                  </form>
                </body>
                </html>
                """.formatted(errorBlock, PATH, csrfField, HtmlUtils.htmlEscape(returnTo));
    }

    private static final class HtmlResponse {

        private final ResponseEntity.BodyBuilder builder;
        private final String body;

        HtmlResponse(HttpStatus status, String body) {
            this.builder = ResponseEntity.status(status)
                    .contentType(HTML)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("Content-Security-Policy", CSP);
            this.body = body;
        }

        HtmlResponse header(String name, String value) {
            builder.header(name, value);
            return this;
        }

        ResponseEntity<String> build() {
            return builder.body(body);
        }
    }
}
