package ru.tpu.oko.stats;

import org.junit.jupiter.api.Test;
import ru.tpu.oko.support.IntegrationTest;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatsIT extends IntegrationTest {
    private static final ZoneId TOMSK = ZoneId.of("Asia/Tomsk");

    private void rating(long productId, Long serviceId, int score, String comment, Instant at) {
        jdbc.update("""
                insert into rating (product_id, service_id, score, comment, rater_hash, source, created_at)
                values (?, ?, ?, ?, ?, 'widget', ?)
                """, productId, serviceId, score, comment, new byte[32], Timestamp.from(at));
        LocalDate day = at.atZone(TOMSK).toLocalDate();
        daily(productId, 0, day, score);
        if (serviceId != null) daily(productId, serviceId, day, score);
    }

    private void daily(long productId, long serviceId, LocalDate day, int score) {
        jdbc.update("""
                insert into rating_daily (product_id, service_id, day, cnt, sum_score, c1, c2, c3, c4, c5)
                values (?, ?, ?, 1, ?, ?, ?, ?, ?, ?)
                on conflict (product_id, service_id, day) do update set
                    cnt = rating_daily.cnt + 1, sum_score = rating_daily.sum_score + excluded.sum_score,
                    c1 = rating_daily.c1 + excluded.c1, c2 = rating_daily.c2 + excluded.c2,
                    c3 = rating_daily.c3 + excluded.c3, c4 = rating_daily.c4 + excluded.c4,
                    c5 = rating_daily.c5 + excluded.c5
                """, productId, serviceId, Date.valueOf(day), score,
                score == 1 ? 1 : 0, score == 2 ? 1 : 0, score == 3 ? 1 : 0,
                score == 4 ? 1 : 0, score == 5 ? 1 : 0);
    }

    @Test
    void acceptedRatingAppearsInAllReadEndpoints() throws Exception {
        long product = createProduct(OWNER, "Кабинет", "https://cabinet.example.org");
        String key = issueKey(OWNER, product, "secret");
        perform(post(API + "/ratings").header("X-Api-Key", key)
                        .contentType("application/json")
                        .content(toJson(java.util.Map.of("score", 4, "raterId", "demo-user", "comment", "Удобно"))))
                .andExpect(status().isCreated());
        perform(get(API + "/products/{id}/stats/summary", product).with(as(OWNER)))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.average").value(4.0));
        perform(get(API + "/products/{id}/ratings", product).with(as(OWNER)))
                .andExpect(jsonPath("$.items[0].comment").value("Удобно"));
        perform(get(API + "/dashboard").with(as(OWNER)))
                .andExpect(jsonPath("$.products[0].rating.count").value(1));
    }

    @Test
    void summaryAndSeriesReadDailyAggregateWithTomskDaysAndEmptyBuckets() throws Exception {
        long product = createProduct(OWNER, "Кабинет", "https://cabinet.example.org");
        long service = createService(OWNER, product, "Расписание");
        Instant first = LocalDate.of(2026, 11, 7).atStartOfDay(TOMSK).toInstant().plusSeconds(60);
        rating(product, service, 5, "Хорошо", first);
        rating(product, null, 1, null, first.plusSeconds(1));
        String base = API + "/products/" + product;

        perform(get(base + "/stats/summary").with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.average").value(3.0))
                .andExpect(jsonPath("$.index").value(0.0))
                .andExpect(jsonPath("$.histogram[0].count").value(1))
                .andExpect(jsonPath("$.histogram[4].count").value(1));
        perform(get(base + "/stats/summary").with(as(OWNER)).param("serviceId", String.valueOf(service)))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.average").value(5.0));
        perform(get(base + "/stats/timeseries").with(as(OWNER))
                        .param("from", "2026-11-07").param("to", "2026-11-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("day"))
                .andExpect(jsonPath("$.points.length()").value(3))
                .andExpect(jsonPath("$.points[1].count").value(0))
                .andExpect(jsonPath("$.points[1].average").value(org.hamcrest.Matchers.nullValue()));
        perform(get(base + "/stats/summary").with(as(OWNER))
                        .param("from", "2026-11-09").param("to", "2026-11-07"))
                .andExpect(status().isBadRequest());
        perform(get(base + "/stats/summary").with(as(STRANGER))).andExpect(status().isNotFound());
        perform(get(base + "/stats/summary").with(as(OWNER)).param("serviceId", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewsFilterAndPageWithoutExposingRaterIdentity() throws Exception {
        long product = createProduct(OWNER, "Кабинет", "https://cabinet.example.org");
        Instant at = clock.instant();
        rating(product, null, 2, "Ошибка", at.minusSeconds(1));
        rating(product, null, 5, null, at);
        var response = perform(get(API + "/products/{id}/ratings", product).with(as(OWNER))
                        .param("hasComment", "true").param("maxScore", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].comment").value("Ошибка"))
                .andReturn();
        assertThat(body(response).get("items").get(0).has("raterHash")).isFalse();
        perform(get(API + "/products/{id}/ratings", product).with(as(OWNER)).param("size", "1").param("page", "2"))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].score").value(2));
        perform(get(API + "/products/{id}/ratings", product).with(as(STRANGER))).andExpect(status().isNotFound());
    }

    @Test
    void dashboardUsesVisibleProductsAndDoesNotHideKnownOutage() throws Exception {
        long product = createProduct(OWNER, "Кабинет", "https://cabinet.example.org");
        long down = createService(OWNER, product, "Недоступный");
        long second = createService(OWNER, product, "Без данных");
        jdbc.update("insert into service_status (service_id, status, checked_at) values (?, 'down', ?)",
                down, Timestamp.from(clock.instant()));
        rating(product, null, 4, null, clock.instant());
        perform(get(API + "/dashboard").with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].health.status").value("down"))
                .andExpect(jsonPath("$.products[0].rating.count").value(1));
        jdbc.update("insert into service_status (service_id, status, checked_at) values (?, 'up', ?)",
                second, Timestamp.from(clock.instant()));
        perform(get(API + "/dashboard").with(as(OWNER)))
                .andExpect(jsonPath("$.products[0].health.status").value("degraded"));
        jdbc.update("update service_status set status = 'up' where service_id = ?", down);
        perform(get(API + "/dashboard").with(as(OWNER)))
                .andExpect(jsonPath("$.products[0].health.status").value("up"));
        perform(get(API + "/dashboard").with(as(STRANGER)))
                .andExpect(jsonPath("$.products.length()").value(0));
    }
}
