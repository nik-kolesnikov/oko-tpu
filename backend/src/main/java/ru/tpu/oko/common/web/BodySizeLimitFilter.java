package ru.tpu.oko.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorWriter;

import java.io.IOException;
import java.util.Set;

/**
 * Лимит тела 16 КБ на приёме оценок. По Content-Length отказываем сразу, без него — считаем байты при чтении.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BodySizeLimitFilter extends OncePerRequestFilter {

    public static final int MAX_BODY_BYTES = 16 * 1024;

    private static final Set<String> LIMITED_PATHS = Set.of(ApiPaths.V1 + "/ratings", ApiPaths.V1 + "/widget/ratings");

    private final ErrorWriter errorWriter;

    public BodySizeLimitFilter(ErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            errorWriter.write(response, ApiException.validation("body", "тело запроса больше 16 КБ"));
            return;
        }
        chain.doFilter(new LimitedRequest(request), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                @Override
                public int read() throws IOException {
                    int b = delegate.read();
                    if (b != -1) {
                        count(1);
                    }
                    return b;
                }

                @Override
                public int read(byte[] buf, int off, int len) throws IOException {
                    int n = delegate.read(buf, off, len);
                    if (n > 0) {
                        count(n);
                    }
                    return n;
                }

                private void count(int n) throws IOException {
                    read += n;
                    if (read > MAX_BODY_BYTES) {
                        // Jackson превратит это в HttpMessageNotReadableException, ответ — 400.
                        throw new IOException("Тело запроса больше " + MAX_BODY_BYTES + " байт");
                    }
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
