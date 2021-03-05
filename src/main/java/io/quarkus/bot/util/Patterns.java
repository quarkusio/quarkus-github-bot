package io.quarkus.bot.util;

import java.util.regex.Pattern;

public class Patterns {

    public static boolean find(String pattern, String string) {
        return Pattern.compile(pattern, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(string)
                .find();
    }

    private Patterns() {
    }
}
