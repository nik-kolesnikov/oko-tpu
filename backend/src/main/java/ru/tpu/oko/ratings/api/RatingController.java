package ru.tpu.oko.ratings.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.ratings.service.RatingIntakeService;

/** Приём оценок. Тела запросов и тексты оценок в логи не пишем. */
@RestController
public class RatingController {

    public static final String API_KEY_HEADER = "X-Api-Key";

    private final RatingIntakeService intake;

    public RatingController(RatingIntakeService intake) {
        this.intake = intake;
    }

    @PostMapping(ApiPaths.V1 + "/widget/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingAccepted fromWidget(@RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
                                     @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
                                     @RequestBody WidgetRatingRequest request, HttpServletRequest http) {
        return new RatingAccepted(intake.submitFromWidget(apiKey, origin, http.getRemoteAddr(), request));
    }

    @PostMapping(ApiPaths.V1 + "/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingAccepted fromServer(@RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
                                     @RequestBody ServerRatingRequest request, HttpServletRequest http) {
        return new RatingAccepted(intake.submitFromServer(apiKey, http.getRemoteAddr(), request));
    }
}
