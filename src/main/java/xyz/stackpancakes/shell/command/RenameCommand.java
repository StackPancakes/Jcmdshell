package xyz.stackpancakes.shell.command;

import xyz.stackpancakes.shell.util.Ansi;
import xyz.stackpancakes.shell.util.ErrorPrinter;
import xyz.stackpancakes.shell.core.Command;
import xyz.stackpancakes.shell.core.CommandResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Renames a file or directory.  If the destination exists it will be
 * overwritten.
 */
public final class RenameCommand implements Command
{
    @Override
    public CommandResult execute(List<String> args)
    {
        if (args.size() != 2)
        {
            ErrorPrinter.setLastError(Ansi.withForeground("Usage", Ansi.Foreground.RED) + ": RENAME <old> <new>");
            return CommandResult.InvalidSyntax;
        }
        Path source = Paths.get(args.getFirst());
        Path destination = Paths.get(args.get(1));
        return renamePath(source, destination);
    }
    private CommandResult renamePath(Path source, Path destination)
    {
        if (!Files.exists(source))
        {
            ErrorPrinter.setLastError("The system cannot find the path specified: " + source);
            return CommandResult.PathNotFound;
        }
        try
        {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception e)
        {
            ErrorPrinter.setLastError("Error renaming file: " + e.getMessage());
            return CommandResult.Failure;
        }
        return CommandResult.Success;
    }
}