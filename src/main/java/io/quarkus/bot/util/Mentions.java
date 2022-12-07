package io.quarkus.bot.util;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Mentions {

    /**
     * Map of mention/username to list of reasons.
     */
    private Map<String, Set<String>> mentions = new TreeMap<>();

    public void add(String mention, String reason) {
        Set<String> reasons = mentions.get(mention);
        if (reasons == null) {
            reasons = new TreeSet<>();
        }
        if (reason != null) {
            reasons.add(reason);
        }
        mentions.put(mention, reasons);
    }

    public boolean isEmpty() {
        return mentions.isEmpty();
    }

    /**
     *
     * @return string of form "@mention1, @mention2(reason1,reason2), @mention3(reason1)"
     */
    public String getMentionsString() {
        return mentions.keySet().stream()
                .map(key -> {
                    Set<String> reasons = mentions.get(key);
                    if (reasons.isEmpty()) {
                        return "@" + key;
                    } else {
                        return "@" + key + reasons.stream().collect(Collectors.joining(",", "(", ")"));
                    }
                })
                .collect(Collectors.joining(", "));
    }
}
