package ru.tpu.oko.monitoring.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Isolated stateless authentication for the monitoring sender. Never logs the token. */
public class MonitoringBearerFilter extends OncePerRequestFilter {

    private final byte[] expected;
    private final ErrorWriter errorWriter;

    public MonitoringBearerFilter(String token, ErrorWriter errorWriter) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (expected.length == 0) {
            errorWriter.write(response, ApiException.unavailable("Приём событий мониторинга не настроен"));
            return;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        byte[] supplied = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (!MessageDigest.isEqual(expected, supplied)) {
            errorWriter.write(response, ApiException.unauthorized("Неверный токен мониторинга"));
            return;
        }
        chain.doFilter(request, response);
    }
}
