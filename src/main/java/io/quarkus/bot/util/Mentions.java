package io.quarkus.bot.util;

import java.util.Collection;
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

    public void add(Collection<String> mentions, String reason) {
        for (String mention : mentions) {
            add(mention, reason);
        }
    }

    public void removeAlreadyParticipating(Collection<String> usersAlreadyParticipating) {
        mentions.keySet().removeAll(usersAlreadyParticipating);
    }

    public boolean isEmpty() {
        return mentions.isEmpty();
    }

    /**
     *
     * @return string of form "@mention1, @mention2(reason1,reason2), @mention3(reason1)"
     */
    public String getMentionsString() {
        if (mentions.isEmpty()) {
            return null;
        }

        return mentions.entrySet().stream()
                .map(es -> {
                    Set<String> reasons = es.getValue();
                    if (reasons.isEmpty()) {
                        return "@" + es.getKey();
                    } else {
                        return "@" + es.getKey() + reasons.stream().collect(Collectors.joining(",", "(", ")"));
                    }
                })
                .collect(Collectors.joining(", "));
    }
}
