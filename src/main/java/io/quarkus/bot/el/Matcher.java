package io.quarkus.bot.el;

import java.util.regex.Pattern;

import io.quarkus.bot.util.Strings;

public class Matcher {

    public static boolean matches(String string, String pattern) {
        if (Strings.isNotBlank(string)) {
            return Pattern.compile(pattern, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(string).matches();
        }

        return false;
    }

    private Matcher() {
    }
}
