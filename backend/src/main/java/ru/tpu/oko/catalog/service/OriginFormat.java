package ru.tpu.oko.catalog.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Строгий формат origin: схема http/https, хост, необязательный порт. Без *, пути, query и завершающего /.
 * Приводим к виду, в котором браузер шлёт заголовок Origin: нижний регистр, без порта по умолчанию.
 */
public final class OriginFormat {

    private static final String LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final Pattern ORIGIN = Pattern.compile(
            "^(https?)://(" + LABEL + "(?:\\." + LABEL + ")*|\\[[0-9a-f:.]+\\])(?::([0-9]{1,5}))?$");

    private OriginFormat() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = ORIGIN.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            return Optional.empty();
        }
        String scheme = m.group(1);
        String host = m.group(2);
        String port = m.group(3);
        if (port != null) {
            int p = Integer.parseInt(port);
            if (p < 1 || p > 65535) {
                return Optional.empty();
            }
            boolean defaultPort = (scheme.equals("https") && p == 443) || (scheme.equals("http") && p == 80);
            port = defaultPort ? null : String.valueOf(p);
        }
        return Optional.of(scheme + "://" + host + (port == null ? "" : ":" + port));
    }
}
