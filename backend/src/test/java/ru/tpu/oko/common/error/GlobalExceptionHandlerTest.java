package ru.tpu.oko.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Отказы, которые сложно вызвать на живой БД: нет соединения, неожиданная ошибка, нарушение индекса. */
class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void databaseUnavailableIs503() throws Exception {
        mvc.perform(get("/db-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"))
                .andExpect(content().string(not(containsString("SQL"))));
    }

    @Test
    void unexpectedErrorIs500WithoutStacktrace() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("секрет"))));
    }

    @Test
    void uniqueIndexViolationIsMappedToSpecCode() throws Exception {
        mvc.perform(get("/duplicate-url"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_URL"));
    }

    @RestController
    static class FailingController {

        @GetMapping("/db-down")
        void dbDown() {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", new SQLException("refused"));
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("внутренний секрет");
        }

        @GetMapping("/duplicate-url")
        void duplicateUrl() {
            throw new DataIntegrityViolationException("x", new SQLException(
                    "ERROR: duplicate key value violates unique constraint \"product_url_uq\""));
        }
    }
}
