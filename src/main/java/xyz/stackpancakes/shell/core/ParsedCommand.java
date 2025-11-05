package xyz.stackpancakes.shell.core;

import java.util.List;

/**
 * Simple record used to store a command name and its arguments after parsing
 * user input.  The arguments list is defensively copied.
 */
public record ParsedCommand(String command, List<String> args)
{
    public ParsedCommand(String command, List<String> args)
    {
        this.command = command;
        this.args = List.copyOf(args);
    }
}