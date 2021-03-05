package io.quarkus.bot.el;

import io.quarkus.bot.util.Patterns;
import io.quarkus.bot.util.Strings;

public class Matcher {

    public static boolean matches(String pattern, String string) {
        if (Strings.isNotBlank(string)) {
            return Patterns.find(pattern, string);
        }

        return false;
    }

    private Matcher() {
    }
}
