package xyz.stackpancakes.shell.command;

import xyz.stackpancakes.shell.util.Ansi;
import xyz.stackpancakes.shell.util.ErrorPrinter;
import xyz.stackpancakes.shell.core.Command;
import xyz.stackpancakes.shell.core.CommandResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies files and directories.  Supports a recursive flag ({@code -r} or
 * {@code --recursive}) to copy directories and their contents.
 */
record CopyArgs(String source, String destination, boolean recursive) {}

public final class CopyCommand implements Command
{
    @Override
    public CommandResult execute(List<String> args)
    {
        try
        {
            CopyArgs parsed = parseArguments(args);
            return performCopy(parsed);
        }
        catch (InvalidArgumentsException e)
        {
            ErrorPrinter.setLastError(Ansi.withForeground("Usage", Ansi.Foreground.RED) + ": " + e.getMessage());
            return CommandResult.InvalidSyntax;
        }
        catch (CopyFailedException e)
        {
            ErrorPrinter.setLastError(Ansi.withForeground("Error", Ansi.Foreground.RED) + ": " + e.getMessage());
            return CommandResult.PathNotFound;
        }
    }
    private CopyArgs parseArguments(List<String> args) throws InvalidArgumentsException
    {
        if (args == null)
            throw new InvalidArgumentsException("COPY [--RECURSIVE | -r] <source> <destination>");
        if (args.size() == 2)
            return new CopyArgs(args.get(0), args.get(1), false);
        if (args.size() == 3)
        {
            boolean recursive = isRecursiveOption(args.get(0));
            if (!recursive)
                throw new InvalidArgumentsException("Unknown option: " + args.get(0));
            return new CopyArgs(args.get(1), args.get(2), true);
        }
        throw new InvalidArgumentsException("COPY [--RECURSIVE | -r] <source> <destination>");
    }
    private boolean isRecursiveOption(String option)
    {
        return option.equalsIgnoreCase("-r") || option.equalsIgnoreCase("--recursive");
    }
    private CommandResult performCopy(CopyArgs args) throws CopyFailedException
    {
        Path source = Paths.get(args.source());
        Path dest = Paths.get(args.destination());
        if (!Files.exists(source))
            throw new CopyFailedException("The system cannot find the path specified: " + source);
        try
        {
            if (args.recursive())
            {
                try (Stream<Path> paths = Files.walk(source))
                {
                    paths.forEach(p ->
                    {
                        try
                        {
                            Path target = dest.resolve(source.relativize(p));
                            if (Files.isDirectory(p))
                                Files.createDirectories(target);
                            else
                                Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        catch (Exception ex)
                        {
                            throw new RuntimeException(ex);
                        }
                    });
                }
            }
            else
            {
                Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (RuntimeException re)
        {
            Throwable cause = re.getCause() != null ? re.getCause() : re;
            throw new CopyFailedException(cause.getMessage());
        }
        catch (Exception e)
        {
            throw new CopyFailedException(e.getMessage());
        }
        return CommandResult.Success;
    }
    private static final class InvalidArgumentsException extends Exception
    {
        public InvalidArgumentsException(String message)
        {
            super(message);
        }
    }
    private static final class CopyFailedException extends Exception
    {
        public CopyFailedException(String message)
        {
            super(message);
        }
    }
}