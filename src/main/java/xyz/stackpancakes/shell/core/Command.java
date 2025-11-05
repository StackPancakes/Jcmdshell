package xyz.stackpancakes.shell.core;

import java.util.List;

/**
 * Functional interface representing a built‑in command.  Implementations are
 * expected to consume a list of arguments and return a {@link CommandResult}
 * indicating success or failure.
 */
@FunctionalInterface
public interface Command
{
    CommandResult execute(List<String> args);
}