package ru.tpu.oko.ratings.service;

import org.springframework.stereotype.Component;
import ru.tpu.oko.common.config.OkoProperties;
import ru.tpu.oko.common.crypto.Crypto;

import java.nio.charset.StandardCharsets;

/**
 * rater_hash = HMAC-SHA256(секрет ОКО, raterId). raterId в открытом виде не хранится и не логируется.
 * Смена секрета делает старые хэши несравнимыми: дубликаты за 14 дней до смены перестанут ловиться.
 */
@Component
public class RaterHasher {

    private final byte[] secret;

    public RaterHasher(OkoProperties properties) {
        this.secret = properties.raterHmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] hash(String raterId) {
        return Crypto.hmacSha256(secret, raterId);
    }
}
