package ru.tpu.oko.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.tpu.oko.auth.repo.AppUser;
import ru.tpu.oko.auth.repo.AppUserRepository;
import ru.tpu.oko.common.security.OkoPrincipal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.tpu.oko.support.Contract.matchesContract;

/**
 * База интеграционных тестов: настоящий PostgreSQL (Testcontainers), профили dev и test, MockMvc со Spring Security.
 * Перед каждым тестом БД очищается, часы возвращаются к текущему времени.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Import(TestInfra.class)
public abstract class IntegrationTest {

    protected static final String API = "/api/v1";
    protected static final String ADMIN = "admin";
    protected static final String OWNER = "ivanov";
    protected static final String STRANGER = "petrov";
    /** oko.public-origin в application-test.yml — origin нашего iframe. */
    protected static final String OKO_ORIGIN = "http://oko.test";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                truncate table monitoring_event, audit_log, service_status_log, service_status, release_mark, rating_daily, rating,
                    widget_settings, allowed_origin, api_key, service, product_owner, product, app_user
                restart identity cascade
                """);
        clock.reset();
    }

    /** Запрос от имени пользователя панели (пользователь создаётся, если его ещё нет). */
    protected RequestPostProcessor as(String login) {
        long id = userId(login);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new OkoPrincipal(id, login), null,
                List.of()));
    }

    protected static RequestPostProcessor xsrf() {
        return request -> {
            String token = UUID.randomUUID().toString();
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    protected long userId(String login) {
        return users.findByLogin(login)
                .map(AppUser::getId)
                .orElseGet(() -> users.save(new AppUser(login, "Тестовый " + login, login + "@example.invalid",
                        clock.instant())).getId());
    }

    /**
     * Изменяющий запрос панели: пользователь, CSRF-токен и JSON-тело. Токен передаётся как в настоящей панели —
     * cookie XSRF-TOKEN и заголовок X-XSRF-TOKEN с тем же значением. Пост-процессор csrf() из spring-security-test
     * не используем: он подменяет репозиторий токенов в общем для тестов контексте.
     */
    protected MockHttpServletRequestBuilder panel(MockHttpServletRequestBuilder request, String login, Object body) {
        request.with(as(login)).with(xsrf());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        }
        return request;
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(matchesContract());
    }

    protected long createProduct(String owner, String name, String url) throws Exception {
        MvcResult result = perform(panel(post(API + "/products"), owner, Map.of("name", name, "url", url)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    protected long createService(String owner, long productId, String name) throws Exception {
        MvcResult result = perform(panel(post(API + "/products/{id}/services", productId), owner, Map.of("name", name)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    /** Выпускает ключ и возвращает его целиком. */
    protected String issueKey(String owner, long productId, String type) throws Exception {
        MvcResult result = perform(panel(post(API + "/products/{id}/keys", productId), owner, Map.of("type", type)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("key").asText();
    }

    protected void setOrigins(String owner, long productId, String... origins) throws Exception {
        perform(panel(put(API + "/products/{id}/origins", productId), owner, Map.of("origins", List.of(origins))))
                .andExpect(status().isOk());
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
