package xyz.stackpancakes.shell.command.function;

import xyz.stackpancakes.shell.command.ChangeDirectoriesCommand;
import xyz.stackpancakes.shell.command.MakeDirectoriesCommand;
import xyz.stackpancakes.shell.core.ReservedWords;
import xyz.stackpancakes.shell.util.Ansi;
import xyz.stackpancakes.shell.util.CurrentDirectory;
import xyz.stackpancakes.shell.util.ErrorPrinter;
import xyz.stackpancakes.shell.core.CommandResult;
import xyz.stackpancakes.shell.util.OutputPrinter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static xyz.stackpancakes.REPL.MAJOR;
import static xyz.stackpancakes.REPL.MINOR;
import static xyz.stackpancakes.REPL.PATCH;

public final class FunctionCommands
{
    private FunctionCommands() {}
    private static final List<String> stringHelp = createStringHelp();
    private static List<String> createStringHelp()
    {
        Map<String, LinkedHashSet<String>> map = new LinkedHashMap<>();
        for (ReservedWords w : ReservedWords.values())
        {
            if (w == ReservedWords.UNKNOWN)
                continue;
            map.computeIfAbsent(w.info(), _ -> new LinkedHashSet<>()).add(w.name());
        }
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : map.entrySet())
        {
            String displayName = String.join("/", e.getValue());
            list.add(displayName + ":" + e.getKey());
        }
        return list;
    }

    public static CommandResult helpCommand()
    {
        for (String entry : stringHelp)
        {
            String[] parts = entry.split(":", 2);
            String nameColored = Ansi.withForeground(String.format("%-16s", parts[0]), Ansi.Foreground.YELLOW);

            String prev = OutputPrinter.getLastOutput().orElse("");

            OutputPrinter.setLastOutput(prev + String.format("| %s -> %s.%n", nameColored, parts[1]));
        }
        return CommandResult.Success;
    }

    public static CommandResult newCommand(List<String> args)
    {
        if (args.size() != 1)
            return invalidUsage("NEW <file>");
        Path filePath = CurrentDirectory.get().resolve(args.getFirst());
        if (Files.exists(filePath))
            return CommandResult.AlreadyExists;
        return createFile(filePath);
    }
    public static CommandResult showCommand(List<String> args)
    {
        if (args.size() != 1)
            return invalidUsage("SHOW <file>");
        Path file = CurrentDirectory.get().resolve(args.getFirst());
        if (!Files.exists(file) || !Files.isRegularFile(file))
            return pathNotFound(file);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), decoder)))
        {
            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line).append(System.lineSeparator());

            OutputPrinter.setLastOutput(sb.toString());
            return CommandResult.Success;
        }
        catch (MalformedInputException e)
        {
            return commandFailure("Malformed input in file: " + e.getMessage() + " skipping...");
        }
        catch (IOException e)
        {
            return commandFailure("Error reading file: " + e.getMessage());
        }
    }
    private static CommandResult createFile(Path filePath)
    {
        try
        {
            Files.createFile(filePath);
            return CommandResult.Success;
        }
        catch (IOException e)
        {
            return commandFailure("Cannot create file: " + e.getMessage());
        }
    }
    public static CommandResult versionCommand()
    {
        String box =
                Ansi.withForeground("+----------------------------------+\n", Ansi.Foreground.BLUE)
                        + Ansi.withForeground("|", Ansi.Foreground.BLUE)
                        + "  Welcome to "
                        + Ansi.withForeground("JCmdShell", Ansi.Foreground.CYAN)
                        + String.format(" (v%s.%s.%s)", MAJOR, MINOR, PATCH)
                        + " ".repeat(3)
                        + Ansi.withForeground("|\n", Ansi.Foreground.BLUE)
                        + Ansi.withForeground("|", Ansi.Foreground.BLUE)
                        + "  Created by "
                        + Ansi.withForeground("StackPancakes", Ansi.Foreground.YELLOW)
                        + " ".repeat(8)
                        + Ansi.withForeground("|\n", Ansi.Foreground.BLUE)
                        + Ansi.withForeground("|", Ansi.Foreground.BLUE)
                        + "  Type '"
                        + Ansi.withForeground("HELP", Ansi.Foreground.GREEN)
                        + "' to get started."
                        + " ".repeat(5)
                        + Ansi.withForeground("|\n", Ansi.Foreground.BLUE)
                        + Ansi.withForeground("+----------------------------------+\n", Ansi.Foreground.BLUE);
        OutputPrinter.setLastOutput(box);
        return CommandResult.Success;
    }
    public static CommandResult printCommand(List<String> args)
    {
        OutputPrinter.setLastOutput(String.join(" ", args));
        return CommandResult.Success;
    }
    public static CommandResult MCDCommand(List<String> args)
    {
        MakeDirectoriesCommand makeDirectoriesCommand = new MakeDirectoriesCommand();
        ChangeDirectoriesCommand changeDirectoriesCommand = new ChangeDirectoriesCommand();
        makeDirectoriesCommand.execute(args);
        changeDirectoriesCommand.execute(args);
        return CommandResult.Success;
    }
    private static CommandResult invalidUsage(String usage)
    {
        ErrorPrinter.setLastError(Ansi.withForeground("Usage", Ansi.Foreground.RED) + ": " + usage);
        return CommandResult.InvalidSyntax;
    }
    private static CommandResult pathNotFound(Path path)
    {
        ErrorPrinter.setLastError("The system cannot find the path specified: " + path);
        return CommandResult.PathNotFound;
    }
    private static CommandResult commandFailure(String message)
    {
        ErrorPrinter.setLastError(message);
        return CommandResult.Failure;
    }
}