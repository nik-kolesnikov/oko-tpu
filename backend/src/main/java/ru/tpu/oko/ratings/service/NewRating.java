package ru.tpu.oko.ratings.service;

import java.time.Instant;

/** Проверенная оценка, готовая к записи. */
public record NewRating(long productId, Long serviceId, int score, String comment, byte[] raterHash, String raterRole,
                        String pagePath, String version, String source, Instant createdAt) {
}
