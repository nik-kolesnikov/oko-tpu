package ru.tpu.oko.auth.service;

/** Защита от открытого редиректа: после входа возвращаем только на относительный путь панели. */
public final class ReturnTo {

    public static final String DEFAULT = "/";

    private ReturnTo() {
    }

    public static String sanitize(String returnTo) {
        if (returnTo == null || returnTo.isEmpty() || returnTo.length() > 2000) {
            return DEFAULT;
        }
        if (!returnTo.startsWith("/") || returnTo.startsWith("//") || returnTo.startsWith("/\\")) {
            return DEFAULT;
        }
        for (int i = 0; i < returnTo.length(); i++) {
            char c = returnTo.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '\\') {
                return DEFAULT;
            }
        }
        return returnTo;
    }
}
