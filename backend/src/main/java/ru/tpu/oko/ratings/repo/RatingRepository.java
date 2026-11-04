package ru.tpu.oko.ratings.repo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.tpu.oko.ratings.service.NewRating;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

/** SQL приёма оценок: advisory lock, проверка дубликата, вставка и upsert агрегата rating_daily. */
@Repository
public class RatingRepository {

    /** service_id = 0 в rating_daily — строка «весь продукт». */
    public static final long WHOLE_PRODUCT = 0L;

    private final JdbcClient jdbc;

    public RatingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * pg_advisory_xact_lock(int, int) по паре (продукт, оценивший). Снимается сам при завершении транзакции.
     * Совпадение ключей у разных пар лишь сериализует их запросы, на корректность не влияет.
     */
    public void lockRater(long productId, byte[] raterHash) {
        int productKey = (int) (productId ^ (productId >>> 32));
        int raterKey = ByteBuffer.wrap(raterHash, 0, 4).getInt();
        jdbc.sql("select pg_advisory_xact_lock(:productKey, :raterKey)")
                .param("productKey", productKey)
                .param("raterKey", raterKey)
                .query(rs -> {
                });
    }

    public boolean existsSince(long productId, byte[] raterHash, Instant since) {
        return jdbc.sql("""
                        select exists(select 1 from rating
                                      where product_id = :productId and rater_hash = :raterHash and created_at > :since)
                        """)
                .param("productId", productId)
                .param("raterHash", raterHash)
                .param("since", Timestamp.from(since))
                .query(Boolean.class)
                .single();
    }

    public long insert(NewRating r) {
        return jdbc.sql("""
                        insert into rating (product_id, service_id, score, scale_max, comment, rater_hash, rater_role,
                                            page_path, version, source, created_at)
                        values (:productId, :serviceId, :score, 5, :comment, :raterHash, :raterRole,
                                :pagePath, :version, :source, :createdAt)
                        returning id
                        """)
                .param("productId", r.productId())
                .param("serviceId", r.serviceId())
                .param("score", r.score())
                .param("comment", r.comment())
                .param("raterHash", r.raterHash())
                .param("raterRole", r.raterRole())
                .param("pagePath", r.pagePath())
                .param("version", r.version())
                .param("source", r.source())
                .param("createdAt", Timestamp.from(r.createdAt()))
                .query(Long.class)
                .single();
    }

    public void addToDaily(long productId, long serviceId, LocalDate day, int score) {
        jdbc.sql("""
                        insert into rating_daily (product_id, service_id, day, cnt, sum_score, c1, c2, c3, c4, c5)
                        values (:productId, :serviceId, :day, 1, :score, :c1, :c2, :c3, :c4, :c5)
                        on conflict (product_id, service_id, day) do update set
                            cnt = rating_daily.cnt + 1,
                            sum_score = rating_daily.sum_score + excluded.sum_score,
                            c1 = rating_daily.c1 + excluded.c1,
                            c2 = rating_daily.c2 + excluded.c2,
                            c3 = rating_daily.c3 + excluded.c3,
                            c4 = rating_daily.c4 + excluded.c4,
                            c5 = rating_daily.c5 + excluded.c5
                        """)
                .param("productId", productId)
                .param("serviceId", serviceId)
                .param("day", day)
                .param("score", score)
                .param("c1", score == 1 ? 1 : 0)
                .param("c2", score == 2 ? 1 : 0)
                .param("c3", score == 3 ? 1 : 0)
                .param("c4", score == 4 ? 1 : 0)
                .param("c5", score == 5 ? 1 : 0)
                .update();
    }
}
