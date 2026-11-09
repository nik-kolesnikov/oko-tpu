package ru.tpu.oko.stats.api;

import ru.tpu.oko.monitoring.HealthView;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StatsDtos {
    private StatsDtos() {}

    public record Bucket(int score, long count) {}
    public record RatingStats(long count, BigDecimal average, BigDecimal index, boolean lowSample,
                              List<Bucket> histogram) {}
    public record Point(LocalDate periodStart, long count, BigDecimal average, boolean lowSample) {}
    public record Timeseries(String granularity, LocalDate from, LocalDate to, List<Point> points) {}
    public record Review(long id, int score, String comment, Long serviceId, String serviceName,
                         String raterRole, String pagePath, String version, String source, Instant createdAt) {}
    public record ReviewPage(int page, int size, long totalItems, long totalPages, List<Review> items) {}
    public record DashboardService(long id, String name, HealthView health, RatingStats rating) {}
    public record ProductHealth(String status, Integer responseMs, Instant checkedAt) {}
    public record DashboardProduct(long id, String name, String status, ProductHealth health,
                                   RatingStats rating, List<DashboardService> services) {}
    public record Dashboard(int ratingPeriodDays, List<DashboardProduct> products) {}
}
