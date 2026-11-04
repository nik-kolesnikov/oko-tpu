package ru.tpu.oko.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Пишет {@link ErrorResponse} там, где нет MVC: в фильтрах и обработчиках Spring Security. */
@Component
public class ErrorWriter {

    private final ObjectMapper objectMapper;

    public ErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, ErrorResponse body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    public void write(HttpServletResponse response, ApiException ex) throws IOException {
        write(response, ex.status(), ex.toResponse());
    }
}
