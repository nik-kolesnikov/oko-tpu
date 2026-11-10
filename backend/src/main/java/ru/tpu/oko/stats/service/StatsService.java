package ru.tpu.oko.stats.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.catalog.service.ProductAccess;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.security.CurrentUser;
import ru.tpu.oko.monitoring.HealthReader;
import ru.tpu.oko.monitoring.HealthStatus;
import ru.tpu.oko.monitoring.HealthView;
import ru.tpu.oko.stats.api.StatsDtos;
import ru.tpu.oko.stats.repo.StatsRepository;
import ru.tpu.oko.stats.repo.StatsRepository.Counts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class StatsService {
    private static final ZoneId TOMSK = ZoneId.of("Asia/Tomsk");
    private final StatsRepository repo;
    private final ProductAccess access;
    private final CurrentUser currentUser;
    private final HealthReader health;
    private final Clock clock;

    public StatsService(StatsRepository repo, ProductAccess access, CurrentUser currentUser,
                        HealthReader health, Clock clock) {
        this.repo = repo;
        this.access = access;
        this.currentUser = currentUser;
        this.health = health;
        this.clock = clock;
    }

    private void requireScope(long productId, Long serviceId) {
        access.requireVisible(productId);
        if (serviceId != null && !repo.serviceBelongsTo(productId, serviceId)) {
            throw ApiException.notFound();
        }
    }

    private static void requirePeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.validation("from", "начало периода позже конца");
        }
    }

    public static StatsDtos.RatingStats stats(Counts c) {
        BigDecimal average = c.count() == 0 ? null : ratio(c.sum(), c.count());
        BigDecimal index = c.count() == 0 ? null :
                BigDecimal.valueOf(c.c4() + c.c5() - c.c1() - c.c2())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(c.count()), 1, RoundingMode.HALF_UP);
        return new StatsDtos.RatingStats(c.count(), average, index, c.count() < 20,
                List.of(new StatsDtos.Bucket(1, c.c1()), new StatsDtos.Bucket(2, c.c2()),
                        new StatsDtos.Bucket(3, c.c3()), new StatsDtos.Bucket(4, c.c4()),
                        new StatsDtos.Bucket(5, c.c5())));
    }

    private static BigDecimal ratio(long sum, long count) {
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public StatsDtos.RatingStats summary(long productId, Long serviceId, LocalDate from, LocalDate to) {
        requireScope(productId, serviceId);
        requirePeriod(from, to);
        return stats(repo.totals(productId, serviceId == null ? 0 : serviceId, from, to));
    }

    private static LocalDate bucket(LocalDate day, String granularity) {
        return switch (granularity) {
            case "week" -> day.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case "month" -> day.withDayOfMonth(1);
            default -> day;
        };
    }

    private static LocalDate next(LocalDate date, String granularity) {
        return switch (granularity) {
            case "week" -> date.plusWeeks(1);
            case "month" -> date.plusMonths(1);
            default -> date.plusDays(1);
        };
    }

    private static Counts plus(Counts a, Counts b) {
        return new Counts(a.count() + b.count(), a.sum() + b.sum(), a.c1() + b.c1(),
                a.c2() + b.c2(), a.c3() + b.c3(), a.c4() + b.c4(), a.c5() + b.c5());
    }

    @Transactional(readOnly = true)
    public StatsDtos.Timeseries timeseries(long productId, Long serviceId, LocalDate from, LocalDate to,
                                            String requestedGranularity) {
        requireScope(productId, serviceId);
        LocalDate end = to == null ? clock.instant().atZone(TOMSK).toLocalDate() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        requirePeriod(start, end);
        String granularity = requestedGranularity.equals("auto")
                ? (java.time.temporal.ChronoUnit.DAYS.between(start, end) < 30 ? "day" : "week")
                : requestedGranularity;
        if (!List.of("day", "week", "month").contains(granularity)) {
            throw ApiException.validation("granularity", "допустимы auto, day, week, month");
        }
        Map<LocalDate, Counts> grouped = new TreeMap<>();
        for (var row : repo.days(productId, serviceId == null ? 0 : serviceId, start, end)) {
            grouped.merge(bucket(row.day(), granularity), row.counts(), StatsService::plus);
        }
        List<StatsDtos.Point> points = new ArrayList<>();
        for (LocalDate day = bucket(start, granularity); !day.isAfter(end); day = next(day, granularity)) {
            Counts c = grouped.getOrDefault(day, Counts.empty());
            points.add(new StatsDtos.Point(day, c.count(),
                    c.count() == 0 ? null : ratio(c.sum(), c.count()), c.count() < 20));
        }
        return new StatsDtos.Timeseries(granularity, start, end, points);
    }

    @Transactional(readOnly = true)
    public StatsDtos.ReviewPage reviews(long productId, Long serviceId, LocalDate from, LocalDate to,
                                         Integer minScore, Integer maxScore, boolean hasComment, String version,
                                         int page, int size) {
        requireScope(productId, serviceId);
        requirePeriod(from, to);
        if (minScore != null && maxScore != null && minScore > maxScore) {
            throw ApiException.validation("minScore", "минимальная оценка выше максимальной");
        }
        Instant start = from == null ? null : from.atStartOfDay(TOMSK).toInstant();
        Instant until = to == null ? null : to.plusDays(1).atStartOfDay(TOMSK).toInstant();
        long total = repo.reviewCount(productId, serviceId, start, until, minScore, maxScore, hasComment, version);
        var items = repo.reviews(productId, serviceId, start, until, minScore, maxScore, hasComment, version, page, size);
        return new StatsDtos.ReviewPage(page, size, total, (total + size - 1) / size, items);
    }

    private static StatsDtos.ProductHealth productHealth(List<StatsDtos.DashboardService> services) {
        long up = services.stream().filter(s -> s.health().status() == HealthStatus.UP).count();
        long down = services.stream().filter(s -> s.health().status() == HealthStatus.DOWN).count();
        String status = down > 0 ? (up > 0 ? "degraded" : "down")
                : up == services.size() && up > 0 ? "up" : "unknown";
        Integer responseMs = services.stream().map(s -> s.health().responseMs())
                .filter(v -> v != null).max(Integer::compareTo).orElse(null);
        Instant checkedAt = services.stream().map(s -> s.health().checkedAt())
                .filter(v -> v != null).max(Instant::compareTo).orElse(null);
        return new StatsDtos.ProductHealth(status, responseMs, checkedAt);
    }

    @Transactional(readOnly = true)
    public StatsDtos.Dashboard dashboard() {
        var products = repo.visibleProducts(currentUser.isAdmin(), currentUser.id());
        var services = repo.activeServices(products.stream().map(StatsRepository.ProductRow::id).toList());
        Map<Long, HealthView> healthByService = health.read(services.stream().map(StatsRepository.ServiceRow::id).toList());
        Map<Long, List<StatsRepository.ServiceRow>> byProduct = services.stream()
                .collect(Collectors.groupingBy(StatsRepository.ServiceRow::productId));
        LocalDate to = clock.instant().atZone(TOMSK).toLocalDate();
        LocalDate from = to.minusDays(29);
        List<StatsDtos.DashboardProduct> result = new ArrayList<>();
        for (var product : products) {
            List<StatsDtos.DashboardService> children = new ArrayList<>();
            for (var service : byProduct.getOrDefault(product.id(), List.of())) {
                children.add(new StatsDtos.DashboardService(service.id(), service.name(),
                        healthByService.getOrDefault(service.id(), HealthView.NO_DATA),
                        stats(repo.totals(product.id(), service.id(), from, to))));
            }
            result.add(new StatsDtos.DashboardProduct(product.id(), product.name(), product.status(),
                    productHealth(children), stats(repo.totals(product.id(), 0, from, to)), children));
        }
        return new StatsDtos.Dashboard(30, result);
    }
}
