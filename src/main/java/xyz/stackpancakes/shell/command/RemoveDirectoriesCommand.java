package xyz.stackpancakes.shell.command;

import xyz.stackpancakes.shell.util.Ansi;
import xyz.stackpancakes.shell.util.ErrorPrinter;
import xyz.stackpancakes.shell.core.Command;
import xyz.stackpancakes.shell.core.CommandResult;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Removes a directory if it exists and is empty.  Does not remove contents
 * recursively.
 */
public final class RemoveDirectoriesCommand implements Command
{
    @Override
    public CommandResult execute(List<String> args)
    {
        if (args.size() != 1)
        {
            ErrorPrinter.setLastError(Ansi.withForeground("Usage", Ansi.Foreground.RED) + ": RMDIR <directory>");
            return CommandResult.InvalidSyntax;
        }
        Path dir = Paths.get(args.getFirst());
        return removeDirectory(dir);
    }
    private CommandResult removeDirectory(Path dir)
    {
        if (!Files.exists(dir))
        {
            ErrorPrinter.setLastError("The system cannot find the path specified.");
            return CommandResult.PathNotFound;
        }
        if (!Files.isDirectory(dir))
        {
            ErrorPrinter.setLastError("The specified path is not a directory.");
            return CommandResult.InvalidSyntax;
        }
        try
        {
            Files.delete(dir);
        }
        catch (DirectoryNotEmptyException e)
        {
            ErrorPrinter.setLastError("The directory is not empty.");
            return CommandResult.Failure;
        }
        catch (AccessDeniedException e)
        {
            ErrorPrinter.setLastError("Access is denied.");
            return CommandResult.Failure;
        }
        catch (IOException e)
        {
            ErrorPrinter.setLastError("The system cannot remove the directory: " + e.getMessage());
            return CommandResult.Failure;
        }
        return CommandResult.Success;
    }
}