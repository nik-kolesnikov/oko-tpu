package ru.tpu.oko.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoTest {

    @Test
    void hmacMatchesRfc4231TestCase2() {
        byte[] mac = Crypto.hmacSha256("Jefe".getBytes(StandardCharsets.UTF_8), "what do ya want for nothing?");
        assertThat(HexFormat.of().formatHex(mac))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void hmacIsStableForSameSecretAndDiffersForAnother() {
        byte[] a = Crypto.hmacSha256("secret-1".getBytes(StandardCharsets.UTF_8), "rater");
        byte[] b = Crypto.hmacSha256("secret-1".getBytes(StandardCharsets.UTF_8), "rater");
        byte[] c = Crypto.hmacSha256("secret-2".getBytes(StandardCharsets.UTF_8), "rater");
        assertThat(a).isEqualTo(b).isNotEqualTo(c).hasSize(32);
    }

    @Test
    void sha256OfKnownValue() {
        assertThat(HexFormat.of().formatHex(Crypto.sha256("abc")))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void randomTokenIsUrlSafeAndUnique() {
        String a = Crypto.randomToken(32);
        assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Crypto.randomToken(32)).isNotEqualTo(a);
    }

    @Test
    void constantTimeEqualsHandlesNull() {
        assertThat(Crypto.constantTimeEquals("a", "a")).isTrue();
        assertThat(Crypto.constantTimeEquals("a", "b")).isFalse();
        assertThat(Crypto.constantTimeEquals(null, "a")).isFalse();
    }
}
