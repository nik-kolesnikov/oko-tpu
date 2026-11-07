package ru.tpu.oko.ratings.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.common.config.OkoProperties;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.common.time.TomskTime;
import ru.tpu.oko.ratings.repo.RatingRepository;

import java.time.Duration;

/**
 * Запись оценки и агрегата в одной транзакции. Внутри — только наша БД: никаких SSO, мониторинга и сетевых вызовов.
 */
@Component
public class RatingWriter {

    private final RatingRepository ratings;
    private final Duration dedupWindow;

    public RatingWriter(RatingRepository ratings, OkoProperties properties) {
        this.ratings = ratings;
        this.dedupWindow = properties.dedupWindow();
    }

    @Transactional
    public long write(NewRating rating) {
        // Без блокировки два параллельных запроса одного человека оба прошли бы проверку дубликата:
        // индекс rating_dedup_idx не уникальный.
        ratings.lockRater(rating.productId(), rating.raterHash());
        if (ratings.existsSince(rating.productId(), rating.raterHash(), rating.createdAt().minus(dedupWindow))) {
            throw ApiException.conflict(ErrorCode.DUPLICATE_RATING,
                    "Оценка этого продукта от вас уже есть за последние " + dedupWindow.toDays() + " дней");
        }
        long id = ratings.insert(rating);
        var day = TomskTime.day(rating.createdAt());
        ratings.addToDaily(rating.productId(), RatingRepository.WHOLE_PRODUCT, day, rating.score());
        if (rating.serviceId() != null) {
            ratings.addToDaily(rating.productId(), rating.serviceId(), day, rating.score());
        }
        return id;
    }
}
