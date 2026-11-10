package ru.tpu.oko.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.tpu.oko.support.IntegrationTest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatusEventIT extends IntegrationTest {

    private static final String TOKEN = "Bearer test-monitoring-token-0123456789abcdef";

    @Test
    void acceptsWithoutPanelSessionAndHandlesDuplicatesAndConflicts() throws Exception {
        long product = createProduct(OWNER, "Портал", "https://events.example.test");
        long service = createMappedService(product, "portal-http");
        Instant checked = clock.instant().minusSeconds(10);
        UUID id = UUID.randomUUID();
        String event = event(id, "portal-http", "UP", "STATUS_CHANGED", checked, 31.644);

        MvcResult accepted = mvc.perform(post(API + "/status/events").header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isAccepted()).andReturn();
        assertThat(body(accepted).get("result").asText()).isEqualTo("accepted");
        assertThat(body(accepted).get("event_id").asText()).isEqualTo(id.toString());
        assertThat(jdbc.queryForObject("select status from service_status where service_id = ?", String.class, service))
                .isEqualTo("up");
        assertThat(jdbc.queryForObject("select response_ms from service_status where service_id = ?", Integer.class, service))
                .isEqualTo(32);

        mvc.perform(post(API + "/status/events").header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isOk());
        mvc.perform(post(API + "/status/events").header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event(id, "portal-http", "DOWN", "STATUS_CHANGED", checked, 31.644)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from service_status_log where service_id = ?",
                Integer.class, service)).isEqualTo(1);
    }

    @Test
    void staleEventsCannotOverwriteNewStateAndHeartbeatDoesNotCreateTransition() throws Exception {
        long product = createProduct(OWNER, "Портал", "https://order.example.test");
        long service = createMappedService(product, "ordered-http");
        Instant newer = clock.instant().minusSeconds(10);
        send(event(UUID.randomUUID(), "ordered-http", "DOWN", "STATUS_CHANGED", newer, null));
        send(event(UUID.randomUUID(), "ordered-http", "UP", "STATUS_CHANGED", newer.minusSeconds(60), 8.0));
        send(event(UUID.randomUUID(), "ordered-http", "DOWN", "HEARTBEAT", newer.plusSeconds(1), null));
        assertThat(jdbc.queryForObject("select status from service_status where service_id = ?", String.class, service))
                .isEqualTo("down");
        assertThat(jdbc.queryForObject("select count(*) from service_status_log where service_id = ?",
                Integer.class, service)).isEqualTo(1);
    }

    @Test
    void buffersUnknownResourceThenBindsOnServiceCreation() throws Exception {
        Instant checked = clock.instant().minusSeconds(5);
        send(event(UUID.randomUUID(), "late-http", "UP", "STATUS_CHANGED", checked, 1.2));
        assertThat(jdbc.queryForObject("select count(*) from monitoring_event where service_id is null", Integer.class))
                .isEqualTo(1);
        long product = createProduct(OWNER, "Портал", "https://late.example.test");
        long service = createMappedService(product, "late-http");
        assertThat(jdbc.queryForObject("select status from service_status where service_id = ?", String.class, service))
                .isEqualTo("up");
        assertThat(jdbc.queryForObject("select count(*) from monitoring_event where service_id = ?", Integer.class,
                service)).isEqualTo(1);
    }

    @Test
    void statusChangeWinsOverHeartbeatAtSameCheckTime() throws Exception {
        long product = createProduct(OWNER, "Портал", "https://tie.example.test");
        long service = createMappedService(product, "tie-http");
        Instant checked = clock.instant().minusSeconds(5);
        send(event(UUID.randomUUID(), "tie-http", "UP", "HEARTBEAT", checked, 4.0));
        send(event(UUID.randomUUID(), "tie-http", "DOWN", "STATUS_CHANGED", checked, null));
        assertThat(jdbc.queryForObject("select status from service_status where service_id = ?", String.class, service))
                .isEqualTo("down");
        assertThat(jdbc.queryForObject("select count(*) from service_status_log where service_id = ?",
                Integer.class, service)).isEqualTo(2);
    }

    @Test
    void requiresBearerAndValidPayload() throws Exception {
        String valid = event(UUID.randomUUID(), "r", "UP", "STATUS_CHANGED", clock.instant(), null);
        mvc.perform(post(API + "/status/events").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(API + "/status/events").header("Authorization", "Bearer wrong")
                .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isUnauthorized());
        mvc.perform(post(API + "/status/events").header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(valid.replace("\"UP\"", "\"BROKEN\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(API + "/status/events").header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(valid.replace("\"response_time_ms\":null,", "")))
                .andExpect(status().isBadRequest());
    }

    private long createMappedService(long productId, String ref) throws Exception {
        MvcResult result = perform(panel(post(API + "/products/{id}/services", productId), OWNER,
                Map.of("name", "Сервис", "monitoringRef", ref))).andExpect(status().isCreated()).andReturn();
        return body(result).get("id").asLong();
    }

    private void send(String event) throws Exception {
        mvc.perform(post(API + "/status/events").header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isAccepted());
    }

    private String event(UUID id, String ref, String state, String type, Instant checked, Double ms) {
        var json = objectMapper.createObjectNode();
        json.put("event_id", id.toString());
        json.put("event_type", type);
        json.put("resource_id", ref);
        json.put("resource_name", "Тестовый ресурс");
        json.put("status", state);
        json.put("check_type", "HTTP");
        json.put("checked_at", checked.toString());
        if (ms == null) json.putNull("response_time_ms"); else json.put("response_time_ms", ms);
        json.putNull("error");
        return json.toString();
    }
}
