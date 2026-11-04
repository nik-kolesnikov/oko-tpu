package ru.tpu.oko.widget;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.tpu.oko.support.IntegrationTest;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WidgetIT extends IntegrationTest {

    private long productId;
    private String publicKey;
    private String secretKey;

    @BeforeEach
    void setUp() throws Exception {
        productId = createProduct(OWNER, "Личный кабинет", "https://lk.tpu.ru");
        publicKey = issueKey(OWNER, productId, "public");
        secretKey = issueKey(OWNER, productId, "secret");
    }

    @Test
    void framePageCarriesFrameAncestorsOfAllowedSites() throws Exception {
        setOrigins(OWNER, productId, "https://lk.tpu.ru", "http://localhost:3000");
        long serviceId = createService(OWNER, productId, "Расписание");
        perform(get(API + "/widget/frame").param("key", publicKey).param("serviceId", String.valueOf(serviceId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        startsWith("frame-ancestors http://localhost:3000 https://lk.tpu.ru;")))
                .andExpect(header().doesNotExist("X-Frame-Options"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("data-key=\"" + publicKey + "\"")))
                .andExpect(content().string(containsString("data-service-id=\"" + serviceId + "\"")))
                .andExpect(content().string(containsString("/widget/v1/frame.js")));
    }

    @Test
    void framePageForbidsEmbeddingWhenNoSitesAllowed() throws Exception {
        perform(get(API + "/widget/frame").param("key", publicKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", startsWith("frame-ancestors 'none';")));
    }

    @Test
    void framePageIsForbiddenForBadKeyOrInactiveProduct() throws Exception {
        perform(get(API + "/widget/frame").param("key", "pk_unknown"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
        perform(get(API + "/widget/frame")).andExpect(status().isForbidden());
        perform(get(API + "/widget/frame").param("key", secretKey)).andExpect(status().isForbidden());
        perform(get(API + "/widget/frame").param("key", publicKey).param("serviceId", "999"))
                .andExpect(status().isForbidden());
        perform(get(API + "/widget/frame").param("key", publicKey).param("serviceId", "<script>"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("<script>"))));

        perform(panel(patch(API + "/products/{id}", productId), OWNER, Map.of("status", "paused")))
                .andExpect(status().isOk());
        perform(get(API + "/widget/frame").param("key", publicKey)).andExpect(status().isForbidden());
    }

    @Test
    void configReturnsSettingsForActiveProduct() throws Exception {
        perform(get(API + "/widget/config").header("X-Api-Key", publicKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.question").value("Как вам сервис?"))
                .andExpect(jsonPath("$.askIntervalDays").value(14))
                .andExpect(jsonPath("$.commentThreshold").value(3))
                .andExpect(jsonPath("$.position").value("bottom-right"));
    }

    @Test
    void configIsDisabledWithoutOtherFieldsForPausedProductOrDisabledWidget() throws Exception {
        perform(panel(put(API + "/products/{id}/widget-settings", productId), OWNER,
                Map.of("question", "Как вам?", "askIntervalDays", 14, "commentThreshold", 3,
                        "position", "bottom-right", "widgetEnabled", false)))
                .andExpect(status().isOk());
        perform(get(API + "/widget/config").header("X-Api-Key", publicKey))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.question").doesNotExist());

        perform(panel(put(API + "/products/{id}/widget-settings", productId), OWNER,
                Map.of("question", "Как вам?", "askIntervalDays", 14, "commentThreshold", 3,
                        "position", "bottom-right", "widgetEnabled", true)))
                .andExpect(status().isOk());
        perform(panel(patch(API + "/products/{id}", productId), OWNER, Map.of("status", "paused")))
                .andExpect(status().isOk());
        perform(get(API + "/widget/config").header("X-Api-Key", publicKey))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"enabled\":false}", true));
    }

    @Test
    void configRejectsMissingOrSecretKey() throws Exception {
        perform(get(API + "/widget/config")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_KEY"));
        perform(get(API + "/widget/config").header("X-Api-Key", secretKey)).andExpect(status().isUnauthorized());
    }
}
