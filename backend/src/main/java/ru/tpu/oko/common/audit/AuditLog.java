package ru.tpu.oko.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Кто что менял: ключи, владельцы, архивация. В details не пишем тексты оценок и персональные данные.
 */
@Component
public class AuditLog {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AuditLog(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void record(long userId, String action, String objectType, Long objectId, Map<String, ?> details) {
        jdbc.sql("""
                        insert into audit_log (user_id, action, object_type, object_id, details)
                        values (:userId, :action, :objectType, :objectId, cast(:details as jsonb))
                        """)
                .param("userId", userId)
                .param("action", action)
                .param("objectType", objectType)
                .param("objectId", objectId)
                .param("details", toJson(details))
                .update();
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
