package ru.tpu.oko.widget.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.ratings.api.RatingController;
import ru.tpu.oko.widget.service.WidgetService;

import java.nio.charset.StandardCharsets;

/** Публичные эндпоинты, которыми пользуется виджет на чужом сайте. Ответы не кэшируются. */
@RestController
@RequestMapping(ApiPaths.V1 + "/widget")
public class WidgetController {

    private final WidgetService widget;

    public WidgetController(WidgetService widget) {
        this.widget = widget;
    }

    /** Ключ в query: у iframe src нельзя задать заголовок. Встраивание ограничивает браузер по CSP frame-ancestors. */
    @GetMapping(value = "/frame", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> frame(@RequestParam(required = false) String key,
                                        @RequestParam(required = false) String serviceId) {
        return widget.frame(key, serviceId)
                .map(frame -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .header("Content-Security-Policy", frame.contentSecurityPolicy())
                        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                        .body(frame.html()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore()).build());
    }

    @GetMapping("/config")
    public ResponseEntity<WidgetConfigDto> config(
            @RequestHeader(value = RatingController.API_KEY_HEADER, required = false) String key,
            HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, RatingController.API_KEY_HEADER)
                .body(widget.config(key, request.getRemoteAddr()));
    }
}
