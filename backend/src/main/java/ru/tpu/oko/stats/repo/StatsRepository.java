package ru.tpu.oko.stats.repo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.tpu.oko.stats.api.StatsDtos.Review;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class StatsRepository {
    private final JdbcClient jdbc;

    public StatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Counts(long count, long sum, long c1, long c2, long c3, long c4, long c5) {
        public static Counts empty() { return new Counts(0, 0, 0, 0, 0, 0, 0); }
    }
    public record DayCounts(LocalDate day, Counts counts) {}
    public record ProductRow(long id, String name, String status) {}
    public record ServiceRow(long id, long productId, String name) {}

    public boolean serviceBelongsTo(long productId, long serviceId) {
        return jdbc.sql("select exists(select 1 from service where id = :serviceId and product_id = :productId)")
                .param("serviceId", serviceId).param("productId", productId).query(Boolean.class).single();
    }

    private static Map<String, Object> aggregateParams(long productId, long serviceId, LocalDate from, LocalDate to) {
        Map<String, Object> params = new HashMap<>();
        params.put("productId", productId);
        params.put("serviceId", serviceId);
        if (from != null) params.put("from", from);
        if (to != null) params.put("to", to);
        return params;
    }

    private static String rangeSql(LocalDate from, LocalDate to) {
        return (from == null ? "" : " and day >= :from") + (to == null ? "" : " and day <= :to");
    }

    public Counts totals(long productId, long serviceId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select coalesce(sum(cnt), 0) n, coalesce(sum(sum_score), 0) total,
                       coalesce(sum(c1), 0) c1, coalesce(sum(c2), 0) c2,
                       coalesce(sum(c3), 0) c3, coalesce(sum(c4), 0) c4, coalesce(sum(c5), 0) c5
                from rating_daily where product_id = :productId and service_id = :serviceId
                """ + rangeSql(from, to))
                .params(aggregateParams(productId, serviceId, from, to))
                .query((rs, n) -> counts(rs)).single();
    }

    public List<DayCounts> days(long productId, long serviceId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select day, cnt n, sum_score total, c1, c2, c3, c4, c5 from rating_daily
                where product_id = :productId and service_id = :serviceId
                """ + rangeSql(from, to) + " order by day")
                .params(aggregateParams(productId, serviceId, from, to))
                .query((rs, n) -> new DayCounts(rs.getObject("day", LocalDate.class), counts(rs))).list();
    }

    private static Counts counts(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Counts(rs.getLong("n"), rs.getLong("total"), rs.getLong("c1"), rs.getLong("c2"),
                rs.getLong("c3"), rs.getLong("c4"), rs.getLong("c5"));
    }

    public List<ProductRow> visibleProducts(boolean admin, long userId) {
        return jdbc.sql("""
                select p.id, p.name, p.status from product p
                where p.status in ('active', 'paused')
                and (:admin or exists(select 1 from product_owner o where o.product_id = p.id and o.user_id = :userId))
                order by lower(p.name), p.id
                """).param("admin", admin).param("userId", userId)
                .query((rs, n) -> new ProductRow(rs.getLong("id"), rs.getString("name"), rs.getString("status"))).list();
    }

    public List<ServiceRow> activeServices(List<Long> productIds) {
        if (productIds.isEmpty()) return List.of();
        return jdbc.sql("""
                select id, product_id, name from service
                where product_id in (:productIds) and status = 'active'
                order by lower(name), id
                """).param("productIds", productIds)
                .query((rs, n) -> new ServiceRow(rs.getLong("id"), rs.getLong("product_id"), rs.getString("name"))).list();
    }

    private static Map<String, Object> reviewParams(long productId, Long serviceId, Instant from, Instant until,
                                                      Integer minScore, Integer maxScore, boolean hasComment,
                                                      String version) {
        Map<String, Object> params = new HashMap<>();
        params.put("productId", productId);
        if (serviceId != null) params.put("serviceId", serviceId);
        if (from != null) params.put("from", Timestamp.from(from));
        if (until != null) params.put("until", Timestamp.from(until));
        if (minScore != null) params.put("minScore", minScore);
        if (maxScore != null) params.put("maxScore", maxScore);
        if (version != null) params.put("version", version);
        return params;
    }

    private static String reviewFilter(Long serviceId, Instant from, Instant until, Integer minScore,
                                       Integer maxScore, boolean hasComment, String version) {
        StringBuilder sql = new StringBuilder(" from rating r left join service s on s.id = r.service_id where r.product_id = :productId");
        if (serviceId != null) sql.append(" and r.service_id = :serviceId");
        if (from != null) sql.append(" and r.created_at >= :from");
        if (until != null) sql.append(" and r.created_at < :until");
        if (minScore != null) sql.append(" and r.score >= :minScore");
        if (maxScore != null) sql.append(" and r.score <= :maxScore");
        if (hasComment) sql.append(" and r.comment is not null and btrim(r.comment) <> ''");
        if (version != null) sql.append(" and r.version = :version");
        return sql.toString();
    }

    public long reviewCount(long productId, Long serviceId, Instant from, Instant until, Integer minScore,
                            Integer maxScore, boolean hasComment, String version) {
        return jdbc.sql("select count(*)" + reviewFilter(serviceId, from, until, minScore, maxScore, hasComment, version))
                .params(reviewParams(productId, serviceId, from, until, minScore, maxScore, hasComment, version))
                .query(Long.class).single();
    }

    public List<Review> reviews(long productId, Long serviceId, Instant from, Instant until, Integer minScore,
                                Integer maxScore, boolean hasComment, String version, int page, int size) {
        Map<String, Object> params = reviewParams(productId, serviceId, from, until, minScore, maxScore, hasComment, version);
        params.put("limit", size);
        params.put("offset", (long) (page - 1) * size);
        return jdbc.sql("""
                select r.id, r.score, r.comment, r.service_id, s.name service_name, r.rater_role,
                       r.page_path, r.version, r.source, r.created_at
                """ + reviewFilter(serviceId, from, until, minScore, maxScore, hasComment, version)
                + " order by r.created_at desc, r.id desc limit :limit offset :offset")
                .params(params)
                .query((rs, n) -> new Review(rs.getLong("id"), rs.getInt("score"), rs.getString("comment"),
                        (Long) rs.getObject("service_id"), rs.getString("service_name"),
                        rs.getString("rater_role"), rs.getString("page_path"), rs.getString("version"),
                        rs.getString("source"), rs.getTimestamp("created_at").toInstant())).list();
    }
}
