package io.quarkus.bot.retest;

/**
 * Formats user-provided command text for bot comments without relying on inline Markdown code spans.
 */
final class RetestCommentFormatter {

    private RetestCommentFormatter() {
    }

    static String formatCommandMessage(String commandLine, String userMessage) {
        return "Command:\n" + fencedCodeBlock(commandLine) + "\n\n" + userMessage;
    }

    private static String fencedCodeBlock(String text) {
        String normalizedText = normalize(text);
        String fence = "`".repeat(Math.max(3, longestBacktickRun(normalizedText) + 1));
        return fence + "text\n" + normalizedText + "\n" + fence;
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static int longestBacktickRun(String text) {
        int longest = 0;
        int current = 0;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '`') {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }

        return longest;
    }
}
