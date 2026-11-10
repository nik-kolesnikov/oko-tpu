package ru.tpu.oko.auth.service;

import org.junit.jupiter.api.Test;
import ru.tpu.oko.common.config.OkoProperties.PasswordLogin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPasswordTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Test
    void disabledWhenBothEmpty() {
        AdminPassword admin = new AdminPassword(new PasswordLogin("", ""));
        assertThat(admin.enabled()).isFalse();
        assertThat(admin.login()).isEmpty();
        assertThat(admin.matches("", "")).isFalse();
    }

    @Test
    void loginAndPasswordAreSetTogether() {
        assertThatThrownBy(() -> new AdminPassword(new PasswordLogin("oko-admin", "")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("вместе");
        assertThatThrownBy(() -> new AdminPassword(new PasswordLogin("", PASSWORD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    void shortPasswordStopsStartupWithoutRevealingIt() {
        String shortPassword = "only15chars-abc";
        assertThat(shortPassword).hasSize(15);
        assertThatThrownBy(() -> new AdminPassword(new PasswordLogin("oko-admin", shortPassword)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16")
                .hasMessageNotContaining(shortPassword);
    }

    @Test
    void loginFormatIsChecked() {
        assertThatThrownBy(() -> new AdminPassword(new PasswordLogin("админ", PASSWORD)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void matchesOnlyExactPasswordAndLoginIgnoringCase() {
        AdminPassword admin = new AdminPassword(new PasswordLogin("OKO-Admin", PASSWORD));
        assertThat(admin.enabled()).isTrue();
        assertThat(admin.login()).contains("oko-admin");
        assertThat(admin.matches("oko-admin", PASSWORD)).isTrue();
        assertThat(admin.matches(" OKO-ADMIN ", PASSWORD)).isTrue();
        assertThat(admin.matches("oko-admin", PASSWORD.toUpperCase())).isFalse();
        assertThat(admin.matches("oko-admin", PASSWORD + " ")).isFalse();
        assertThat(admin.matches("someone", PASSWORD)).isFalse();
        assertThat(admin.matches("oko-admin", "")).isFalse();
        assertThat(admin.matches(null, PASSWORD)).isFalse();
        assertThat(admin.matches("oko-admin", "x".repeat(5000))).isFalse();
    }

    @Test
    void passwordIsMaskedInToString() {
        assertThat(new PasswordLogin("oko-admin", PASSWORD).toString()).doesNotContain(PASSWORD);
    }
}
