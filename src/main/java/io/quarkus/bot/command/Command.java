package io.quarkus.bot.command;

import org.kohsuke.github.ReactionContent;

import java.io.IOException;
import java.util.List;

public interface Command<T> {

    List<String> labels();

    ReactionContent run(T input) throws IOException;

}
