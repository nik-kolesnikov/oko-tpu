package ru.tpu.oko.ratings.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Схема {@code ServerRatingRequest}: raterId обязателен и стабилен в пределах продукта. */
public record ServerRatingRequest(
        @NotNull(message = "обязательное поле") @Min(value = 1, message = "должно быть от 1 до 5")
        @Max(value = 5, message = "должно быть от 1 до 5")
        Integer score,
        @Size(max = 500, message = "не длиннее 500 символов")
        String comment,
        @NotNull(message = "обязательное поле") @Size(min = 1, max = 128, message = "от 1 до 128 символов")
        String raterId,
        RaterRole raterRole,
        Long serviceId,
        @Size(max = 500, message = "не длиннее 500 символов")
        String pagePath,
        @Size(max = 50, message = "не длиннее 50 символов")
        String version) {
}
