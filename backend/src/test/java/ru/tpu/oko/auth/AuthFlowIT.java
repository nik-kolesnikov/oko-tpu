package ru.tpu.oko.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import ru.tpu.oko.support.IntegrationTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Вход через заглушку SSO целиком: state, сессия, CSRF как у настоящей панели, выход. */
class AuthFlowIT extends IntegrationTest {

    @Test
    void fullLoginFlowCreatesSessionAndUser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String state = startLogin(session, "/products/12");
        String sessionIdBefore = session.getId();

        perform(get(API + "/auth/callback").session(session).param("code", OWNER).param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/products/12"));
        assertThat(session.getId()).as("идентификатор сессии меняется после входа").isNotEqualTo(sessionIdBefore);

        perform(get(API + "/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(OWNER))
                .andExpect(jsonPath("$.isAdmin").value(false));
        assertThat(jdbc.queryForObject("select count(*) from app_user where login = ?", Long.class, OWNER)).isOne();
    }

    @Test
    void adminIsDeterminedByConfig() throws Exception {
        MockHttpSession session = login(ADMIN);
        perform(get(API + "/auth/me").session(session)).andExpect(jsonPath("$.isAdmin").value(true));
    }

    @Test
    void callbackWithWrongOrMissingStateIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        startLogin(session, null);
        perform(get(API + "/auth/callback").session(session).param("code", OWNER).param("state", "forged"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("state"));
        perform(get(API + "/auth/callback").param("code", OWNER).param("state", "no-session"))
                .andExpect(status().isBadRequest());
        perform(get(API + "/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void stateCannotBeReused() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String state = startLogin(session, null);
        perform(get(API + "/auth/callback").session(session).param("code", OWNER).param("state", state))
                .andExpect(status().isFound());
        perform(get(API + "/auth/callback").session(session).param("code", STRANGER).param("state", state))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCodeIsUnauthorized() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String state = startLogin(session, null);
        perform(get(API + "/auth/callback").session(session).param("code", "НЕ ЛОГИН").param("state", state))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnToAcceptsOnlyRelativePaths() throws Exception {
        for (String evil : new String[]{"//evil.example/x", "https://evil.example", "/\\evil.example"}) {
            MockHttpSession session = new MockHttpSession();
            String state = startLogin(session, evil);
            perform(get(API + "/auth/callback").session(session).param("code", OWNER).param("state", state))
                    .andExpect(header().string("Location", "/"));
        }
    }

    @Test
    void csrfWorksLikeInRealPanel() throws Exception {
        MockHttpSession session = login(OWNER);
        MvcResult me = perform(get(API + "/auth/me").session(session)).andReturn();
        Cookie xsrf = me.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).as("cookie XSRF-TOKEN выставляется для панели").isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();

        String body = toJson(Map.of("name", "Личный кабинет", "url", "https://lk.tpu.ru"));
        perform(post(API + "/products").session(session).cookie(xsrf)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        perform(post(API + "/products").session(session).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void logoutEndsSession() throws Exception {
        MockHttpSession session = login(OWNER);
        MvcResult me = perform(get(API + "/auth/me").session(session)).andReturn();
        Cookie xsrf = me.getResponse().getCookie("XSRF-TOKEN");
        perform(post(API + "/auth/logout").session(session).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
        perform(get(API + "/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void panelEndpointsRequireSession() throws Exception {
        perform(get(API + "/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        perform(get(API + "/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void devLoginPageEscapesState() throws Exception {
        mvc.perform(get(API + "/auth/dev-login").param("state", "\"><script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void passwordLoginIsOffByDefault() throws Exception {
        // OKO_ADMIN_LOGIN и OKO_ADMIN_PASSWORD не заданы — страницы входа по паролю нет, вход идёт через SSO (здесь — заглушку).
        perform(get(API + "/auth/password-login")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        perform(post(API + "/auth/password-login").with(xsrf()).param("login", ADMIN).param("password", "x".repeat(20)))
                .andExpect(status().isNotFound());
    }

    private String startLogin(MockHttpSession session, String returnTo) throws Exception {
        var request = get(API + "/auth/login").session(session);
        if (returnTo != null) {
            request.param("returnTo", returnTo);
        }
        MvcResult result = perform(request).andExpect(status().isFound()).andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).startsWith(API + "/auth/dev-login");
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
    }

    private MockHttpSession login(String login) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String state = startLogin(session, null);
        perform(get(API + "/auth/callback").session(session).param("code", login).param("state", state))
                .andExpect(status().isFound());
        return session;
    }
}
