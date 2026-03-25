package io.quarkus.bot.retest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetestCommentFormatterTest {

    @Test
    void shouldUseLongEnoughFenceForCommandsContainingBackticks() {
        String message = RetestCommentFormatter.formatCommandMessage("@quarkusbot ```retest```",
                ":rotating_light: boom");

        assertThat(message).contains("````text\n@quarkusbot ```retest```\n````");
    }
}
