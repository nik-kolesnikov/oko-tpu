package ru.tpu.oko.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.tpu.oko.support.IntegrationTest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIT extends IntegrationTest {

    // ── Продукты ──

    @Test
    void creatorBecomesOwnerAndGetsDefaultWidgetSettings() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        perform(get(API + "/products/{id}", id).with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.owners[0].login").value(OWNER));
        perform(get(API + "/products/{id}/widget-settings", id).with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("Как вам сервис?"))
                .andExpect(jsonPath("$.askIntervalDays").value(14))
                .andExpect(jsonPath("$.commentThreshold").value(3))
                .andExpect(jsonPath("$.position").value("bottom-right"))
                .andExpect(jsonPath("$.widgetEnabled").value(true));
    }

    @Test
    void createValidatesFields() throws Exception {
        perform(panel(post(API + "/products"), OWNER, Map.of("name", "Л", "url", "lk.tpu.ru")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"));
        perform(panel(post(API + "/products"), OWNER, Map.of("name", "Личный кабинет", "url", "ftp://lk.tpu.ru")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("url"));
    }

    @Test
    void urlIsUniqueAmongNonArchivedProductsIgnoringCase() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        perform(panel(post(API + "/products"), STRANGER, Map.of("name", "Копия", "url", "https://LK.TPU.ru")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_URL"));

        perform(panel(delete(API + "/products/{id}", id), ADMIN, null)).andExpect(status().isNoContent());
        createProduct(STRANGER, "Новый кабинет", "https://lk.tpu.ru");
    }

    @Test
    void listShowsOwnProductsToOwnerAndEverythingToAdmin() throws Exception {
        createProduct(OWNER, "Расписание", "https://rasp.tpu.ru");
        createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        createProduct(STRANGER, "Электронная почта", "https://mail.tpu.ru");

        perform(get(API + "/products").with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Личный кабинет"));
        perform(get(API + "/products").with(as(ADMIN)))
                .andExpect(jsonPath("$.totalItems").value(3));
        perform(get(API + "/products").with(as(ADMIN)).param("q", "ПОЧТ"))
                .andExpect(jsonPath("$.items", hasSize(1)));
        perform(get(API + "/products").with(as(ADMIN)).param("q", "%"))
                .andExpect(jsonPath("$.totalItems").value(0));
        perform(get(API + "/products").with(as(ADMIN)).param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        long archived = createProduct(OWNER, "Старый портал", "https://old.tpu.ru");
        createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        perform(panel(delete(API + "/products/{id}", archived), ADMIN, null)).andExpect(status().isNoContent());

        perform(get(API + "/products").with(as(OWNER))).andExpect(jsonPath("$.totalItems").value(1));
        perform(get(API + "/products").with(as(OWNER)).param("status", "archived"))
                .andExpect(jsonPath("$.items[0].status").value("archived"));
        perform(get(API + "/products").with(as(OWNER)).param("status", "active", "archived"))
                .andExpect(jsonPath("$.totalItems").value(2));
        // В спецификации у listProducts нет ответа 400 (см. docs/team/Расхождения в документах.md),
        // поэтому здесь без проверки контракта.
        mvc.perform(get(API + "/products").with(as(OWNER)).param("status", "deleted"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("status"));
        mvc.perform(get(API + "/products").with(as(OWNER)).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("size"));
    }

    @Test
    void foreignProductLooksLikeNotFound() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        perform(get(API + "/products/{id}", id).with(as(STRANGER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        perform(get(API + "/products/{id}/keys", id).with(as(STRANGER))).andExpect(status().isNotFound());
        perform(panel(delete(API + "/products/{id}", id), STRANGER, null)).andExpect(status().isNotFound());
        perform(get(API + "/products/{id}", 999_999).with(as(ADMIN))).andExpect(status().isNotFound());
    }

    @Test
    void updateChangesFieldsAndPausesProduct() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        Map<String, Object> body = new HashMap<>();
        body.put("description", "Кабинет студента");
        body.put("status", "paused");
        perform(panel(patch(API + "/products/{id}", id), OWNER, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paused"))
                .andExpect(jsonPath("$.description").value("Кабинет студента"))
                .andExpect(jsonPath("$.name").value("Личный кабинет"));

        Map<String, Object> clear = new HashMap<>();
        clear.put("description", null);
        perform(panel(patch(API + "/products/{id}", id), OWNER, clear))
                .andExpect(jsonPath("$.description").doesNotExist());

        perform(panel(patch(API + "/products/{id}", id), OWNER, Map.of("status", "archived")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRejectsUrlOfAnotherProduct() throws Exception {
        createProduct(OWNER, "Почта", "https://mail.tpu.ru");
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        perform(panel(patch(API + "/products/{id}", id), OWNER, Map.of("url", "https://MAIL.tpu.ru")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_URL"));
        perform(panel(patch(API + "/products/{id}", id), OWNER, Map.of("url", "https://LK.tpu.ru/")))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminArchivesAndArchiveRevokesKeysAndDisablesWidget() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        issueKey(OWNER, id, "public");
        issueKey(OWNER, id, "secret");

        perform(panel(delete(API + "/products/{id}", id), OWNER, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        perform(panel(delete(API + "/products/{id}", id), ADMIN, null)).andExpect(status().isNoContent());
        perform(panel(delete(API + "/products/{id}", id), ADMIN, null)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from api_key where revoked_at is null", Long.class)).isZero();
        perform(get(API + "/products/{id}/widget-settings", id).with(as(OWNER)))
                .andExpect(jsonPath("$.widgetEnabled").value(false));
        perform(panel(patch(API + "/products/{id}", id), OWNER, Map.of("name", "Новое имя")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        perform(panel(post(API + "/products/{id}/keys", id), OWNER, Map.of("type", "public")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'product.archive'", Long.class))
                .isOne();
    }

    @Test
    void adminCanCreateProductForTeam() throws Exception {
        userId(OWNER);
        perform(panel(post(API + "/products"), ADMIN,
                Map.of("name", "Личный кабинет", "url", "https://lk.tpu.ru", "ownerLogins", List.of(OWNER))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owners", hasSize(1)))
                .andExpect(jsonPath("$.owners[0].login").value(OWNER));

        perform(panel(post(API + "/products"), ADMIN,
                Map.of("name", "Почта", "url", "https://mail.tpu.ru", "ownerLogins", List.of("never-logged-in"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("ownerLogins[0]"));
        perform(panel(post(API + "/products"), OWNER,
                Map.of("name", "Почта", "url", "https://mail.tpu.ru", "ownerLogins", List.of(OWNER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutatingRequestWithoutCsrfIsForbidden() throws Exception {
        perform(post(API + "/products").with(as(OWNER)).contentType("application/json")
                .content(toJson(Map.of("name", "Личный кабинет", "url", "https://lk.tpu.ru"))))
                .andExpect(status().isForbidden());
    }

    // ── Владельцы ──

    @Test
    void ownersCanBeAddedOnlyFromKnownUsersAndLastOwnerStays() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        long ownerId = userId(OWNER);
        long strangerId = userId(STRANGER);

        perform(panel(post(API + "/products/{id}/owners", id), OWNER, Map.of("login", "nobody")))
                .andExpect(status().isNotFound());
        perform(panel(post(API + "/products/{id}/owners", id), OWNER, Map.of("login", STRANGER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value(STRANGER));
        perform(panel(post(API + "/products/{id}/owners", id), OWNER, Map.of("login", STRANGER)))
                .andExpect(status().isConflict());
        perform(get(API + "/products/{id}/owners", id).with(as(STRANGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));

        perform(panel(delete(API + "/products/{id}/owners/{u}", id, ownerId), STRANGER, null))
                .andExpect(status().isNoContent());
        perform(panel(delete(API + "/products/{id}/owners/{u}", id, strangerId), STRANGER, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER"));
        perform(panel(delete(API + "/products/{id}/owners/{u}", id, ownerId), STRANGER, null))
                .andExpect(status().isNotFound());
    }

    // ── Сервисы ──

    @Test
    void servicesHaveUnknownHealthUntilMonitoringReports() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        long fresh = createService(OWNER, id, "Расписание");
        long stale = createService(OWNER, id, "Почта");
        createService(OWNER, id, "Без данных");
        Instant now = clock.instant();
        jdbc.update("insert into service_status (service_id, status, response_ms, checked_at) values (?, 'up', 120, ?)",
                fresh, java.sql.Timestamp.from(now.minusSeconds(30)));
        jdbc.update("insert into service_status (service_id, status, response_ms, checked_at) values (?, 'down', 900, ?)",
                stale, java.sql.Timestamp.from(now.minusSeconds(600)));

        perform(get(API + "/products/{id}/services", id).with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name == 'Расписание')].health.status").value("up"))
                .andExpect(jsonPath("$.items[?(@.name == 'Расписание')].health.responseMs").value(120))
                .andExpect(jsonPath("$.items[?(@.name == 'Почта')].health.status").value("unknown"))
                .andExpect(jsonPath("$.items[?(@.name == 'Без данных')].health.status").value("unknown"));
    }

    @Test
    void monitoringRefIsGloballyUnique() throws Exception {
        long first = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        long second = createProduct(OWNER, "Почта", "https://mail.tpu.ru");
        perform(panel(post(API + "/products/{id}/services", first), OWNER,
                Map.of("name", "Расписание", "monitoringRef", "mon-42"))).andExpect(status().isCreated());
        perform(panel(post(API + "/products/{id}/services", second), OWNER,
                Map.of("name", "Веб-почта", "monitoringRef", "mon-42")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MONITORING_REF"));
    }

    @Test
    void serviceUpdateAndArchive() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        long serviceId = createService(OWNER, id, "Расписание");
        Map<String, Object> body = new HashMap<>();
        body.put("url", "https://rasp.tpu.ru");
        body.put("monitoringRef", "mon-7");
        perform(panel(patch(API + "/products/{p}/services/{s}", id, serviceId), OWNER, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://rasp.tpu.ru"))
                .andExpect(jsonPath("$.monitoringRef").value("mon-7"));

        perform(panel(delete(API + "/products/{p}/services/{s}", id, serviceId), OWNER, null))
                .andExpect(status().isNoContent());
        perform(get(API + "/products/{id}/services", id).with(as(OWNER))).andExpect(jsonPath("$.items", hasSize(0)));
        perform(get(API + "/products/{id}/services", id).with(as(OWNER)).param("includeArchived", "true"))
                .andExpect(jsonPath("$.items[0].status").value("archived"));
        perform(panel(patch(API + "/products/{p}/services/{s}", id + 100, serviceId), ADMIN, Map.of("name", "X")))
                .andExpect(status().isNotFound());
    }

    // ── Ключи ──

    @Test
    void keyIsShownOnceAndOnlyPrefixIsListed() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        MvcResult issued = perform(panel(post(API + "/products/{id}/keys", id), OWNER, Map.of("type", "secret")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        String key = body(issued).get("key").asText();
        assertThat(key).startsWith("sk_").hasSize(46);
        assertThat(body(issued).get("keyPrefix").asText()).isEqualTo(key.substring(0, 11));

        MvcResult list = perform(get(API + "/products/{id}/keys", id).with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].key").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdBy.login").value(OWNER))
                .andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain(key);
        assertThat(jdbc.queryForObject("select count(*) from api_key where key_prefix = ?", Long.class,
                key.substring(0, 11))).isOne();
    }

    @Test
    void reissueRevokesOldKeyAndRevokeIsIdempotent() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        issueKey(OWNER, id, "public");
        long keyId = jdbc.queryForObject("select id from api_key", Long.class);

        perform(panel(post(API + "/products/{p}/keys/{k}/reissue", id, keyId), OWNER, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("public"))
                .andExpect(jsonPath("$.key").exists());
        perform(panel(post(API + "/products/{p}/keys/{k}/reissue", id, keyId), OWNER, null))
                .andExpect(status().isConflict());
        perform(get(API + "/products/{id}/keys", id).with(as(OWNER))).andExpect(jsonPath("$.items", hasSize(1)));
        perform(get(API + "/products/{id}/keys", id).with(as(OWNER)).param("includeRevoked", "true"))
                .andExpect(jsonPath("$.items", hasSize(2)));

        long newKeyId = jdbc.queryForObject("select id from api_key where revoked_at is null", Long.class);
        perform(panel(post(API + "/products/{p}/keys/{k}/revoke", id, newKeyId), OWNER, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());
        perform(panel(post(API + "/products/{p}/keys/{k}/revoke", id, newKeyId), OWNER, null))
                .andExpect(status().isOk());
        perform(panel(post(API + "/products/{id}/keys", id), OWNER, Map.of("type", "master")))
                .andExpect(status().isBadRequest());
    }

    // ── Разрешённые сайты ──

    @Test
    void originsAreValidatedNormalizedAndReplacedWhole() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        perform(panel(put(API + "/products/{id}/origins", id), OWNER,
                Map.of("origins", List.of("https://LK.tpu.ru:443", "https://lk.tpu.ru", "http://localhost:3000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origins", hasSize(2)))
                .andExpect(jsonPath("$.origins[0]").value("http://localhost:3000"))
                .andExpect(jsonPath("$.origins[1]").value("https://lk.tpu.ru"));

        perform(panel(put(API + "/products/{id}/origins", id), OWNER,
                Map.of("origins", List.of("https://lk.tpu.ru/", "*", "https://*.tpu.ru", "https://lk.tpu.ru/path"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(4)));

        perform(panel(put(API + "/products/{id}/origins", id), OWNER, Map.of("origins", List.of("https://rasp.tpu.ru"))))
                .andExpect(jsonPath("$.origins", hasSize(1)));
        perform(get(API + "/products/{id}/origins", id).with(as(OWNER)))
                .andExpect(jsonPath("$.origins[0]").value("https://rasp.tpu.ru"));
    }

    // ── Настройки виджета ──

    @Test
    void widgetSettingsAreReplacedWholeAndValidated() throws Exception {
        long id = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        Map<String, Object> settings = Map.of("question", "Удобно ли пользоваться кабинетом?", "askIntervalDays", 30,
                "commentThreshold", 2, "position", "bottom-left", "widgetEnabled", false);
        perform(panel(put(API + "/products/{id}/widget-settings", id), OWNER, settings))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.askIntervalDays").value(30))
                .andExpect(jsonPath("$.position").value("bottom-left"))
                .andExpect(jsonPath("$.widgetEnabled").value(false));

        perform(panel(put(API + "/products/{id}/widget-settings", id), OWNER,
                Map.of("question", "?", "askIntervalDays", 91, "commentThreshold", 0, "position", "top",
                        "widgetEnabled", true)))
                .andExpect(status().isBadRequest());
        perform(put(API + "/products/{id}/widget-settings", id).with(as(OWNER)).with(xsrf())
                .contentType("application/json").content("{\"question\":\"?\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(4)));
    }
}
