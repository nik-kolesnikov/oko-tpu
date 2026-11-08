package ru.tpu.oko.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.tpu.oko.common.config.OkoProperties;
import ru.tpu.oko.common.crypto.Crypto;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Вход админа по паролю из окружения (OKO_ADMIN_LOGIN, OKO_ADMIN_PASSWORD), пока SSO ТПУ не подключён.
 * Это не заглушка dev-login: войти можно под одним логином и только с паролем. Работает в любом профиле, если обе
 * переменные заданы; по умолчанию выключен.
 *
 * <p>В памяти держим только SHA-256 логина и пароля, сравниваем за постоянное время. Неверная настройка (задана одна
 * переменная, пароль короче 16 символов) останавливает старт приложения, а сообщение об ошибке пароль не содержит.
 */
@Component
public class AdminPassword {

    public static final int MIN_PASSWORD_LENGTH = 16;
    static final Pattern LOGIN = Pattern.compile("[a-z0-9._-]{2,64}");
    /** Длиннее этого вход заведомо неверный: не тратим время на хэш мегабайтной строки. */
    private static final int MAX_INPUT_LENGTH = 1024;

    private final String login;
    private final byte[] loginHash;
    private final byte[] passwordHash;

    @Autowired
    public AdminPassword(OkoProperties properties) {
        this(properties.passwordLogin());
    }

    AdminPassword(OkoProperties.PasswordLogin config) {
        String configuredLogin = config.login().toLowerCase(Locale.ROOT);
        String password = config.password();
        if (configuredLogin.isEmpty() && password.isEmpty()) {
            this.login = null;
            this.loginHash = null;
            this.passwordHash = null;
            return;
        }
        if (configuredLogin.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException("Вход админа по паролю: OKO_ADMIN_LOGIN и OKO_ADMIN_PASSWORD задаются "
                    + "вместе. Чтобы выключить вход, оставьте обе пустыми");
        }
        if (!LOGIN.matcher(configuredLogin).matches()) {
            throw new IllegalStateException("Вход админа по паролю: OKO_ADMIN_LOGIN — 2–64 символа, латиница, "
                    + "цифры, . _ -");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Вход админа по паролю: OKO_ADMIN_PASSWORD короче " + MIN_PASSWORD_LENGTH
                    + " символов. Сгенерируйте: openssl rand -base64 24");
        }
        this.login = configuredLogin;
        this.loginHash = Crypto.sha256(configuredLogin);
        this.passwordHash = Crypto.sha256(password);
    }

    public boolean enabled() {
        return login != null;
    }

    /** Логин админа, если вход включён. Он всегда админ (см. AdminRegistry). */
    public Optional<String> login() {
        return Optional.ofNullable(login);
    }

    /**
     * Проверяет логин и пароль. Оба сравнения выполняются всегда (без короткого замыкания), чтобы по времени ответа
     * нельзя было понять, что логин угадан. Регистр логина не важен, пароля — важен.
     */
    public boolean matches(String inputLogin, String inputPassword) {
        if (!enabled() || inputLogin == null || inputPassword == null
                || inputLogin.length() > MAX_INPUT_LENGTH || inputPassword.length() > MAX_INPUT_LENGTH) {
            return false;
        }
        boolean loginOk = MessageDigest.isEqual(Crypto.sha256(inputLogin.trim().toLowerCase(Locale.ROOT)), loginHash);
        boolean passwordOk = MessageDigest.isEqual(Crypto.sha256(inputPassword), passwordHash);
        return loginOk & passwordOk;
    }
}
