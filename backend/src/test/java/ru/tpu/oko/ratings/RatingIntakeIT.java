package ru.tpu.oko.ratings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import ru.tpu.oko.support.IntegrationTest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.tpu.oko.support.Contract.matchesContract;

class RatingIntakeIT extends IntegrationTest {

    private static final String SITE = "https://lk.tpu.ru";

    private long productId;
    private long serviceId;
    private String publicKey;
    private String secretKey;

    @BeforeEach
    void setUp() throws Exception {
        productId = createProduct(OWNER, "Личный кабинет", SITE);
        serviceId = createService(OWNER, productId, "Расписание");
        publicKey = issueKey(OWNER, productId, "public");
        secretKey = issueKey(OWNER, productId, "secret");
        setOrigins(OWNER, productId, SITE);
    }

    // ── Успешный приём и агрегаты ──

    @Test
    void serverRatingIsStoredWithProductAndServiceAggregates() throws Exception {
        server(secretKey, Map.of("score", 2, "raterId", "student-1", "serviceId", serviceId,
                "comment", "Долго грузится расписание", "raterRole", "student", "version", "2.4.1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        Map<String, Object> row = jdbc.queryForMap("select * from rating");
        assertThat(row).containsEntry("score", 2).containsEntry("source", "api")
                .containsEntry("rater_role", "student").containsEntry("service_id", serviceId);
        // raterId не хранится в открытом виде.
        assertThat((byte[]) row.get("rater_hash")).hasSize(32);
        assertThat(jdbc.queryForObject("select count(*) from rating where rater_hash = convert_to('student-1', 'UTF8')",
                Long.class)).isZero();

        assertThat(daily(0)).containsEntry("cnt", 1).containsEntry("sum_score", 2).containsEntry("c2", 1);
        assertThat(daily(serviceId)).containsEntry("cnt", 1).containsEntry("sum_score", 2).containsEntry("c2", 1);
    }

    @Test
    void widgetRatingFromOwnIframeOriginIsAccepted() throws Exception {
        widget(publicKey, OKO_ORIGIN, Map.of("score", 5, "raterId", "3f6a2c9e-7a1b-4c53-9d0e-5b1f8a2c4d77"))
                .andExpect(status().isCreated());
        Map<String, Object> row = jdbc.queryForMap("select source, rater_role, service_id from rating");
        assertThat(row).containsEntry("source", "widget").containsEntry("rater_role", "unknown");
        assertThat(row.get("service_id")).isNull();
        assertThat(daily(0)).containsEntry("c5", 1);
    }

    @Test
    void widgetRatingFromAllowedSiteIsAccepted() throws Exception {
        widget(publicKey, "https://LK.tpu.ru", Map.of("score", 4, "raterId", "browser-uuid-0001"))
                .andExpect(status().isCreated());
    }

    @Test
    void ratingDayIsTomskCalendarDay() throws Exception {
        // 17:30 UTC = 00:30 следующего дня по Томску (UTC+7).
        clock.set(Instant.parse("2026-11-04T17:30:00Z"));
        server(secretKey, Map.of("score", 3, "raterId", "night-owl")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select day from rating_daily where service_id = 0", LocalDate.class))
                .isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void ratingAndDailyAggregatesAlwaysReconcile() throws Exception {
        long secondService = createService(OWNER, productId, "Почта");
        Random random = new Random(42);
        for (int i = 0; i < 40; i++) {
            clock.advance(Duration.ofHours(7));
            Map<String, Object> body = new HashMap<>(Map.of("score", 1 + random.nextInt(5), "raterId", "rater-" + i));
            int pick = random.nextInt(3);
            if (pick == 1) {
                body.put("serviceId", serviceId);
            } else if (pick == 2) {
                body.put("serviceId", secondService);
            }
            server(secretKey, body).andExpect(status().isCreated());
        }
        // Строка продукта (service_id = 0) — все оценки, строки сервисов — только оценки с этим сервисом.
        List<Map<String, Object>> mismatches = jdbc.queryForList("""
                with raw as (
                    select 0::bigint as service_id, count(*) cnt, sum(score) s,
                           count(*) filter (where score = 1) c1, count(*) filter (where score = 5) c5
                    from rating where product_id = ?
                    union all
                    select service_id, count(*), sum(score),
                           count(*) filter (where score = 1), count(*) filter (where score = 5)
                    from rating where product_id = ? and service_id is not null group by service_id
                ), agg as (
                    select service_id, sum(cnt) cnt, sum(sum_score) s, sum(c1) c1, sum(c5) c5
                    from rating_daily where product_id = ? group by service_id
                )
                select * from raw full join agg using (service_id)
                where raw.cnt is distinct from agg.cnt or raw.s is distinct from agg.s
                   or raw.c1 is distinct from agg.c1 or raw.c5 is distinct from agg.c5
                """, productId, productId, productId);
        assertThat(mismatches).isEmpty();
        assertThat(jdbc.queryForObject("select sum(cnt) from rating_daily where service_id = 0", Long.class))
                .isEqualTo(40L);
    }

    // ── Ключи ──

    @Test
    void unknownOrMissingKeyIsRejected() throws Exception {
        server("sk_unknown", Map.of("score", 5, "raterId", "x")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_KEY"));
        perform(post(API + "/ratings").contentType(MediaType.APPLICATION_JSON).content("{\"score\":5,\"raterId\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keyOfWrongTypeIsRejected() throws Exception {
        server(publicKey, Map.of("score", 5, "raterId", "x")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_KEY"));
        widget(secretKey, OKO_ORIGIN, Map.of("score", 5, "raterId", "browser-uuid-0001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_KEY"));
    }

    @Test
    void revokedKeyStopsWorkingImmediately() throws Exception {
        server(secretKey, Map.of("score", 5, "raterId", "before-revoke")).andExpect(status().isCreated());
        long keyId = jdbc.queryForObject("select id from api_key where key_type = 'secret'", Long.class);
        perform(panel(post(API + "/products/{p}/keys/{k}/revoke", productId, keyId), OWNER, null))
                .andExpect(status().isOk());
        server(secretKey, Map.of("score", 5, "raterId", "after-revoke")).andExpect(status().isUnauthorized());
    }

    @Test
    void keyIsCheckedBeforeBodyFormat() throws Exception {
        server("sk_unknown", Map.of("score", 42)).andExpect(status().isUnauthorized());
    }

    // ── Статус продукта ──

    @Test
    void pausedProductDoesNotAcceptRatings() throws Exception {
        perform(panel(patch(API + "/products/{id}", productId), OWNER, Map.of("status", "paused")))
                .andExpect(status().isOk());
        server(secretKey, Map.of("score", 5, "raterId", "x")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
        widget(publicKey, OKO_ORIGIN, Map.of("score", 5, "raterId", "browser-uuid-0001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
    }

    @Test
    void archivedProductKeysAreRevoked() throws Exception {
        perform(panel(delete(API + "/products/{id}", productId), ADMIN, null)).andExpect(status().isNoContent());
        server(secretKey, Map.of("score", 5, "raterId", "x")).andExpect(status().isUnauthorized());
        widget(publicKey, OKO_ORIGIN, Map.of("score", 5, "raterId", "browser-uuid-0001"))
                .andExpect(status().isUnauthorized());
    }

    // ── Origin ──

    @Test
    void foreignOrMissingOriginIsRejected() throws Exception {
        widget(publicKey, "https://evil.example", Map.of("score", 5, "raterId", "browser-uuid-0001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        widget(publicKey, null, Map.of("score", 5, "raterId", "browser-uuid-0001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        assertThat(jdbc.queryForObject("select count(*) from rating", Long.class)).isZero();
    }

    // ── Дубликаты ──

    @Test
    void secondRatingWithinFourteenDaysIsDuplicate() throws Exception {
        server(secretKey, Map.of("score", 5, "raterId", "student-7")).andExpect(status().isCreated());
        clock.advance(Duration.ofDays(13));
        server(secretKey, Map.of("score", 1, "raterId", "student-7")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RATING"));
        clock.advance(Duration.ofDays(2));
        server(secretKey, Map.of("score", 1, "raterId", "student-7")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select sum(cnt) from rating_daily where service_id = 0", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void duplicateIsCheckedPerProduct() throws Exception {
        long otherProduct = createProduct(OWNER, "Электронная почта", "https://mail.tpu.ru");
        String otherKey = issueKey(OWNER, otherProduct, "secret");
        server(secretKey, Map.of("score", 5, "raterId", "student-7")).andExpect(status().isCreated());
        server(otherKey, Map.of("score", 5, "raterId", "student-7")).andExpect(status().isCreated());
    }

    @Test
    void parallelRequestsOfOnePersonStoreExactlyOneRating() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        String body = toJson(Map.of("score", 4, "raterId", "same-person"));
        for (int i = 0; i < threads; i++) {
            Callable<Integer> call = () -> {
                start.await();
                return mvc.perform(post(API + "/ratings").header("X-Api-Key", secretKey)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from rating", Long.class)).isEqualTo(1L);
        assertThat(daily(0)).containsEntry("cnt", 1);
    }

    // ── Формат ──

    @Test
    void invalidFieldsAreReportedWithDetails() throws Exception {
        server(secretKey, Map.of("score", 6, "raterId", "x", "comment", "a".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field == 'score')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'comment')]").exists());
        widget(publicKey, OKO_ORIGIN, Map.of("score", 5, "raterId", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("raterId"));
        assertThat(jdbc.queryForObject("select count(*) from rating", Long.class)).isZero();
    }

    @Test
    void commentOfExactly500CharactersIsAccepted() throws Exception {
        server(secretKey, Map.of("score", 1, "raterId", "x", "comment", "я".repeat(500)))
                .andExpect(status().isCreated());
    }

    @Test
    void serviceMustBelongToProductAndBeActive() throws Exception {
        long otherProduct = createProduct(OWNER, "Электронная почта", "https://mail.tpu.ru");
        long foreignService = createService(OWNER, otherProduct, "Веб-почта");
        server(secretKey, Map.of("score", 5, "raterId", "x", "serviceId", foreignService))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("serviceId"));

        perform(panel(delete(API + "/products/{p}/services/{s}", productId, serviceId), OWNER, null))
                .andExpect(status().isNoContent());
        server(secretKey, Map.of("score", 5, "raterId", "x", "serviceId", serviceId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bodyLargerThan16KbIsRejected() throws Exception {
        server(secretKey, Map.of("score", 5, "raterId", "x", "pagePath", "/" + "a".repeat(17 * 1024)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("body"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        perform(post(API + "/ratings").header("X-Api-Key", secretKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\": \"пять\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void responsesAreJsonWithNosniff() throws Exception {
        server(secretKey, Map.of("score", 5, "raterId", "x"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/json"));
    }

    // ── Вспомогательное ──

    private ResultActions server(String key, Map<String, ?> body) throws Exception {
        return mvc.perform(post(API + "/ratings").header("X-Api-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(matchesContract());
    }

    private ResultActions widget(String key, String origin, Map<String, ?> body) throws Exception {
        var request = post(API + "/widget/ratings").header("X-Api-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        if (origin != null) {
            request.header("Origin", origin);
        }
        return mvc.perform(request).andExpect(matchesContract());
    }

    private Map<String, Object> daily(long service) {
        return jdbc.queryForMap("select cnt, sum_score, c1, c2, c3, c4, c5 from rating_daily where service_id = ?",
                service);
    }
}
