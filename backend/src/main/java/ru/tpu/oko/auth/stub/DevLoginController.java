package ru.tpu.oko.auth.stub;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import ru.tpu.oko.common.ApiPaths;

/**
 * Страница входа заглушки. Эндпоинта нет в спецификации: он существует только в профиле dev
 * и заменяет страницу oauth.tpu.ru, пока к SSO нет доступа.
 */
@RestController
@Profile("dev")
public class DevLoginController {

    @GetMapping(value = ApiPaths.V1 + "/auth/dev-login", produces = MediaType.TEXT_HTML_VALUE)
    public String form(@RequestParam(defaultValue = "") String state) {
        String safeState = HtmlUtils.htmlEscape(state);
        return """
                <!doctype html>
                <html lang="ru">
                <head><meta charset="utf-8"><title>ОКО — вход (заглушка)</title></head>
                <body style="font-family: sans-serif; max-width: 420px; margin: 64px auto">
                  <h1>Вход в ОКО (dev)</h1>
                  <p>Заглушка SSO ТПУ. Введите любой логин. Админы задаются в OKO_ADMIN_LOGINS (по умолчанию admin).</p>
                  <form method="get" action="%s/auth/callback">
                    <input type="hidden" name="state" value="%s">
                    <label>Логин <input name="code" required pattern="[a-z0-9._-]{2,64}" autofocus></label>
                    <button type="submit">Войти</button>
                  </form>
                </body>
                </html>
                """.formatted(ApiPaths.V1, safeState);
    }
}
