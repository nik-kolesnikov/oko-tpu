package ru.tpu.oko.stats.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.stats.service.StatsService;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping(ApiPaths.V1)
public class StatsController {
    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/dashboard")
    public StatsDtos.Dashboard dashboard() {
        return stats.dashboard();
    }

    @GetMapping("/products/{productId}/stats/summary")
    public StatsDtos.RatingStats summary(@PathVariable long productId,
                                         @RequestParam(required = false) Long serviceId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.summary(productId, serviceId, from, to);
    }

    @GetMapping("/products/{productId}/stats/timeseries")
    public StatsDtos.Timeseries timeseries(@PathVariable long productId,
                                           @RequestParam(required = false) Long serviceId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "auto") String granularity) {
        return stats.timeseries(productId, serviceId, from, to, granularity);
    }

    @GetMapping("/products/{productId}/ratings")
    public StatsDtos.ReviewPage reviews(@PathVariable long productId,
                                        @RequestParam(required = false) Long serviceId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) @Min(1) @Max(5) Integer minScore,
                                        @RequestParam(required = false) @Min(1) @Max(5) Integer maxScore,
                                        @RequestParam(defaultValue = "false") boolean hasComment,
                                        @RequestParam(required = false) @Size(max = 50) String version,
                                        @RequestParam(defaultValue = "1") @Min(1) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return stats.reviews(productId, serviceId, from, to, minScore, maxScore, hasComment, version, page, size);
    }
}
