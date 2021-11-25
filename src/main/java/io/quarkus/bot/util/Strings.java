package io.quarkus.bot.util;

public class Strings {

    public static boolean isNotBlank(String string) {
        return string != null && !string.trim().isEmpty();
    }

    public static boolean isBlank(String string) {
        return string == null || string.trim().isEmpty();
    }

    private Strings() {
    }
}
