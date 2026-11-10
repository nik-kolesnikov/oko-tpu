package ru.tpu.oko.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.tpu.oko.support.IntegrationTest;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Вход админа по паролю без SSO: профиль test без dev, то есть как на сервере до подключения SSO — заглушки нет,
 * заданы OKO_ADMIN_LOGIN и OKO_ADMIN_PASSWORD.
 */
@ActiveProfiles(value = "test", inheritProfiles = false)
@TestPropertySource(properties = {
        "oko.password-login.login=oko-admin",
        "oko.password-login.password=" + PasswordLoginIT.PASSWORD,
        "oko.rate-limit.password-login-per-ip=5",
        "oko.rate-limit.password-login-total=20"})
class PasswordLoginIT extends IntegrationTest {

    static final String PASSWORD = "test-only-admin-password-0123";
    private static final String LOGIN_PATH = API + "/auth/password-login";
    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Test
    void loginWithoutSsoRedirectsToPasswordPage() throws Exception {
        perform(get(API + "/auth/login").param("returnTo", "/products/12"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", LOGIN_PATH + "?returnTo=/products/12"));
        perform(get(API + "/auth/login").param("returnTo", "https://evil.example"))
                .andExpect(header().string("Location", LOGIN_PATH + "?returnTo=/"));
    }

    @Test
    void devStubIsAbsent() throws Exception {
        mvc.perform(get(API + "/auth/dev-login")).andExpect(status().isNotFound());
    }

    @Test
    void formLoginCreatesAdminSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String sessionIdBefore = session.getId();
        Form form = openForm(session, "/products/12");

        perform(post(LOGIN_PATH).session(session).cookie(form.cookie()).with(ip("10.0.0.1"))
                .param("login", "oko-admin").param("password", PASSWORD)
                .param("returnTo", "/products/12").param("_csrf", form.token()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/products/12"));
        assertThat(session.getId()).as("идентификатор сессии меняется после входа").isNotEqualTo(sessionIdBefore);

        // В OKO_ADMIN_LOGINS (application-test.yml) только admin: oko-admin — админ потому, что это логин входа по паролю.
        perform(get(API + "/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("oko-admin"))
                .andExpect(jsonPath("$.isAdmin").value(true));
    }

    @Test
    void wrongPasswordIsUnauthorizedWithoutHint() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (String[] attempt : new String[][]{{"oko-admin", "wrong-password-0123456"}, {"someone", PASSWORD}}) {
            perform(attempt(session, attempt[0], attempt[1], "10.0.0.2"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(containsString("Неверный логин или пароль")))
                    .andExpect(content().string(not(containsString(PASSWORD))));
        }
        perform(get(API + "/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void returnToIsSanitized() throws Exception {
        perform(attempt(new MockHttpSession(), "oko-admin", PASSWORD, "10.0.0.3").param("returnTo", "//evil.example"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        perform(post(LOGIN_PATH).with(ip("10.0.0.4")).param("login", "oko-admin").param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void pageEscapesReturnToAndHasNoScripts() throws Exception {
        perform(get(LOGIN_PATH).param("returnTo", "/x\"><script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(content().string(not(containsString("<script>alert"))));
    }

    @Test
    void attemptsAreRateLimited() throws Exception {
        // Свежее минутное окно лимитера: попытки других тестов этого класса сюда не попадают.
        clock.advance(Duration.ofDays(1));
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < 5; i++) {
            perform(attempt(session, "oko-admin", "wrong-password-0123456", "10.0.1.1"))
                    .andExpect(status().isUnauthorized());
        }
        perform(attempt(session, "oko-admin", PASSWORD, "10.0.1.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        perform(get(API + "/auth/me").session(session)).andExpect(status().isUnauthorized());

        // Общий лимит: 20 попыток в минуту со всех адресов (5 уже засчитаны выше, шестая упёрлась в лимит по IP).
        for (int i = 0; i < 15; i++) {
            perform(attempt(new MockHttpSession(), "oko-admin", "wrong-password-0123456", "10.0.2." + i))
                    .andExpect(status().isUnauthorized());
        }
        perform(attempt(new MockHttpSession(), "oko-admin", PASSWORD, "10.0.3.1"))
                .andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofMinutes(1));
        perform(attempt(new MockHttpSession(), "oko-admin", PASSWORD, "10.0.1.1")).andExpect(status().isFound());
    }

    // ── Вспомогательное ──

    private record Form(Cookie cookie, String token) {
    }

    /** Открывает форму как браузер: cookie XSRF-TOKEN из ответа и XOR-токен из скрытого поля. */
    private Form openForm(MockHttpSession session, String returnTo) throws Exception {
        MvcResult page = perform(get(LOGIN_PATH).session(session).param("returnTo", returnTo))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn();
        Cookie cookie = page.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("форма выставляет cookie XSRF-TOKEN").isNotNull();
        Matcher m = CSRF_FIELD.matcher(page.getResponse().getContentAsString());
        assertThat(m.find()).as("в форме есть скрытое поле _csrf").isTrue();
        return new Form(cookie, m.group(1));
    }

    private MockHttpServletRequestBuilder attempt(MockHttpSession session, String login, String password, String ip)
            throws Exception {
        Form form = openForm(session, "/");
        return post(LOGIN_PATH).session(session).cookie(form.cookie()).with(ip(ip))
                .param("login", login).param("password", password).param("_csrf", form.token());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
