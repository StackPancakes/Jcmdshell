package xyz.stackpancakes.shell.command;

import xyz.stackpancakes.shell.util.Ansi;
import xyz.stackpancakes.shell.util.ErrorPrinter;
import xyz.stackpancakes.shell.core.Command;
import xyz.stackpancakes.shell.core.CommandResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Removes files or directories.  When the {@code -r} or {@code --recursive}
 * option is supplied the directory and its contents are removed; otherwise
 * only a single file may be deleted.
 */
public final class RemoveCommand implements Command
{
    @Override
    public CommandResult execute(List<String> args)
    {
        if (args.isEmpty())
        {
            ErrorPrinter.setLastError(Ansi.withForeground("Usage", Ansi.Foreground.RED) + ": REMOVE [--RECURSIVE | -r] <path>");
            return CommandResult.InvalidSyntax;
        }
        boolean recursive = false;
        Path target;
        if (args.size() == 1)
            target = Paths.get(args.getFirst());
        else if (args.size() == 2)
        {
            if (!isRecursiveOption(args.getFirst()))
            {
                ErrorPrinter.setLastError(Ansi.withForeground("Unknown option", Ansi.Foreground.RED) + ": " + args.getFirst());
                return CommandResult.UnknownOption;
            }
            recursive = true;
            target = Paths.get(args.get(1));
        }
        else
        {
            ErrorPrinter.setLastError(Ansi.withForeground("Usage", Ansi.Foreground.RED) + ": REMOVE [-r | --RECURSIVE] <path>");
            return CommandResult.InvalidSyntax;
        }
        return removePath(target, recursive);
    }
    private static boolean isRecursiveOption(String option)
    {
        return option.equals("-r") || option.equalsIgnoreCase("--RECURSIVE");
    }
    private CommandResult removePath(Path target, boolean recursive)
    {
        if (!Files.exists(target))
        {
            ErrorPrinter.setLastError("The system cannot find the path specified: " + target);
            return CommandResult.PathNotFound;
        }
        try
        {
            if (Files.isDirectory(target))
            {
                if (!recursive)
                {
                    ErrorPrinter.setLastError("The specified path is a directory. Use -r to remove recursively.");
                    return CommandResult.InvalidSyntax;
                }
                try (Stream<Path> stream = Files.walk(target))
                {
                    stream.sorted(Comparator.reverseOrder()).forEach(path ->
                    {
                        try
                        {
                            Files.delete(path);
                        }
                        catch (IOException ignored) {}
                    });
                }
            }
            else
                Files.delete(target);
        }
        catch (IOException e)
        {
            ErrorPrinter.setLastError(Ansi.withForeground("Error", Ansi.Foreground.RED) + " removing '" + target + "': " + e.getMessage());
            return CommandResult.PathNotFound;
        }
        return CommandResult.Success;
    }
}