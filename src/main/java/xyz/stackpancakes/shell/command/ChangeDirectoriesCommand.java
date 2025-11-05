package xyz.stackpancakes.shell.command;

import xyz.stackpancakes.shell.util.Ansi;
import xyz.stackpancakes.shell.util.CurrentDirectory;
import xyz.stackpancakes.shell.util.ErrorPrinter;
import xyz.stackpancakes.shell.util.FileSystemUtils;
import xyz.stackpancakes.shell.core.Command;
import xyz.stackpancakes.shell.core.CommandResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class ChangeDirectoriesCommand implements Command
{
    @Override
    public CommandResult execute(List<String> args)
    {
        try
        {
            Path target = resolveTargetDirectory(args);
            return changeDirectory(target);
        }
        catch (InvalidArgumentsException e)
        {
            ErrorPrinter.setLastError(Ansi.withForeground("Usage", Ansi.Foreground.RED) + ": " + e.getMessage());
            return CommandResult.InvalidSyntax;
        }
        catch (IOException e)
        {
            ErrorPrinter.setLastError(Ansi.withForeground("Error", Ansi.Foreground.RED) + ": " + e.getMessage());
            return CommandResult.Failure;
        }
    }
    private Path resolveTargetDirectory(List<String> args) throws InvalidArgumentsException, IOException
    {
        if (args == null || args.isEmpty())
        {
            String home = FileSystemUtils.getHomeDirectory();
            if (home == null || home.isEmpty())
                throw new InvalidArgumentsException("Home directory not found.");
            return Paths.get(home).toRealPath();
        }
        if (args.size() != 1)
            throw new InvalidArgumentsException("CD <directory>");
        Path currentDir = getCurrentDirectory();
        String targetPath = args.getFirst();
        if (targetPath.equals(".."))
            return resolveParentDirectory(currentDir);
        else if (targetPath.equals(".") || targetPath.equals("./"))
            return currentDir;
        else if (targetPath.equals("~"))
            return Paths.get(FileSystemUtils.getHomeDirectory()).toRealPath();
        else if (targetPath.startsWith("~/"))
        {
            Path homePath = Paths.get(FileSystemUtils.getHomeDirectory());
            return homePath.resolve(targetPath.substring(2)).toRealPath();
        }
        else
            return resolveNormalDirectory(currentDir, targetPath);
    }
    private Path resolveParentDirectory(Path currentDir) throws IOException
    {
        Path parent = currentDir.getParent();
        if (parent == null)
            throw new IOException("Already at root directory");
        return parent.toRealPath();
    }
    private Path resolveNormalDirectory(Path currentDir, String targetPath) throws IOException
    {
        Path target = Paths.get(targetPath);
        if (target.isAbsolute())
            return target.toRealPath();
        Path resolved = currentDir.resolve(target).normalize();
        if (!Files.exists(resolved))
            throw new IOException("The system cannot find the path specified: " + targetPath);
        if (!Files.isDirectory(resolved))
            throw new IOException("The directory name is invalid: " + targetPath);
        return resolved.toRealPath();
    }
    private CommandResult changeDirectory(Path dir)
    {
        CurrentDirectory.set(dir);
        return CommandResult.Success;
    }
    public static Path getCurrentDirectory()
    {
        return CurrentDirectory.get();
    }
    private static final class InvalidArgumentsException extends Exception
    {
        public InvalidArgumentsException(String message)
        {
            super(message);
        }
    }
}