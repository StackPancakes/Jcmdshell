package xyz.stackpancakes.shell.command;

import xyz.stackpancakes.shell.util.Ansi;
import xyz.stackpancakes.shell.util.ErrorPrinter;
import xyz.stackpancakes.shell.util.FileSystemUtils;
import xyz.stackpancakes.shell.core.Command;
import xyz.stackpancakes.shell.core.CommandResult;
import xyz.stackpancakes.shell.util.OutputPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lists the contents of a directory and colours different file types.  If no
 * argument is supplied the current directory is listed.  Directories are
 * coloured blue with white text, executable files green and symlinks cyan.
 */
public final class ListDirectoriesCommand implements Command
{
    @Override
    public CommandResult execute(List<String> args)
    {
        try
        {
            Path currentDir = ChangeDirectoriesCommand.getCurrentDirectory();
            Path dir = args.isEmpty() ? currentDir : resolvePath(args.getFirst(), currentDir);
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
            try (Stream<Path> stream = Files.list(dir))
            {
                stream.forEach(this::printEntry);
            }
            catch (IOException e)
            {
                ErrorPrinter.setLastError("Error reading directory: " + e.getMessage());
                return CommandResult.Failure;
            }
            return CommandResult.Success;
        }
        catch (Exception e)
        {
            ErrorPrinter.setLastError("Error: " + e.getMessage());
            return CommandResult.Failure;
        }
    }
    private Path resolvePath(String path, Path currentDir) throws IOException
    {
        Path resolved = Paths.get(path);
        if (path.equals(".."))
            return currentDir.getParent() != null ? currentDir.getParent().toRealPath() : currentDir.toRealPath();
        else if (path.equals("."))
            return currentDir.toRealPath();
        if (!resolved.isAbsolute())
            resolved = currentDir.resolve(resolved).normalize();
        return resolved.toRealPath();
    }

    private void printEntry(Path entry)
    {
        String name = entry.getFileName().toString();
        String color = determineColor(entry);

        String prev = OutputPrinter.getLastOutput().orElse("");

        OutputPrinter.setLastOutput(prev + color + name + (color.isEmpty() ? "" : Ansi.RESET) + System.lineSeparator());
    }

    private String determineColor(Path entry)
    {
        try
        {
            if (Files.isDirectory(entry))
                return Ansi.background(Ansi.Background.BLUE) + Ansi.foreground(Ansi.Foreground.WHITE);
            if (Files.isSymbolicLink(entry))
                return Ansi.foreground(Ansi.Foreground.CYAN);
            if (FileSystemUtils.isExecutable(entry))
                return Ansi.foreground(Ansi.Foreground.GREEN);
            return Ansi.foreground(Ansi.Foreground.WHITE);
        }
        catch (Exception e)
        {
            return Ansi.foreground(Ansi.Foreground.WHITE);
        }
    }
}