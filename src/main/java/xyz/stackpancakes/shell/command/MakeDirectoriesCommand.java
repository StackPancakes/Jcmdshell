package xyz.stackpancakes.shell.command;

import xyz.stackpancakes.shell.util.ErrorPrinter;
import xyz.stackpancakes.shell.core.Command;
import xyz.stackpancakes.shell.core.CommandResult;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Built‑in command for creating directories.  Returns appropriate
 * {@link CommandResult} codes based on whether the operation succeeds or
 * fails.
 */
public class MakeDirectoriesCommand implements Command
{
    @Override
    public CommandResult execute(List<String> args)
    {
        if (args.size() != 1)
            return invalidUsage();
        return createDirectory(args.getFirst());
    }
    private CommandResult createDirectory(String dir)
    {
        if (dir == null || dir.isBlank())
            return invalidUsage();
        Path path = Paths.get(dir);
        try
        {
            Files.createDirectories(path);
            return CommandResult.Success;
        }
        catch (FileAlreadyExistsException e)
        {
            ErrorPrinter.setLastError("A subdirectory or file already exists.");
            return CommandResult.AlreadyExists;
        }
        catch (SecurityException e)
        {
            ErrorPrinter.setLastError("Access is denied.");
            return CommandResult.AccessDenied;
        }
        catch (IOException e)
        {
            ErrorPrinter.setLastError("The system cannot create the directory: " + e.getMessage());
            return CommandResult.PathNotFound;
        }
    }
    private CommandResult invalidUsage()
    {
        ErrorPrinter.setLastError("Usage: MKDIR <directory>");
        return CommandResult.InvalidSyntax;
    }
}