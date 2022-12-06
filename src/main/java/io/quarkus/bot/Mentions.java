package io.quarkus.bot;

import java.util.*;
import java.util.stream.Collectors;

public class Mentions {

    /**
     * map of mention/username to list of reasons
     */
    Map<String, Set<String>> mentions = new TreeMap();

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
     * @return string of form "mentionid1,mentionid2(reason1,reason2),mention3(reason1)"
     */
    public String getMentionString() {
        return String.join(", @",
                mentions.keySet().stream()
                        .map(key -> {
                            Set reasons = mentions.get(key);
                            if (reasons.isEmpty()) {
                                return "@" + key;
                            } else {
                                return "@" + key + reasons.stream().collect(Collectors.joining(",", "(", ")"));
                            }
                        })
                        .collect(Collectors.joining(",")));
    }

    public void add(String mention) {
        add(mention, null);
    }
}
