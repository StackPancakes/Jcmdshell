package xyz.stackpancakes;

import xyz.stackpancakes.shell.command.*;
import xyz.stackpancakes.shell.command.function.FunctionCommands;
import xyz.stackpancakes.shell.core.ParsedCommand;
import xyz.stackpancakes.shell.core.ReservedWords;
import xyz.stackpancakes.shell.core.CommandResult;
import xyz.stackpancakes.shell.util.*;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Function;

public final class REPL
{
    public static final byte MAJOR = 0;
    public static final byte MINOR = 1;
    public static final byte PATCH = 0;

    private static final Map<ReservedWords, Function<List<String>, CommandResult>> commands = new EnumMap<>(ReservedWords.class);

    public REPL()
    {
        register(ReservedWords.DIR, args -> new ListDirectoriesCommand().execute(args));
        register(ReservedWords.CHDIR, args -> new ChangeDirectoriesCommand().execute(args));
        register(ReservedWords.COPY, args -> new CopyCommand().execute(args));
        register(ReservedWords.DELETE, args -> new RemoveCommand().execute(args));
        register(ReservedWords.MKDIR, args -> new MakeDirectoriesCommand().execute(args));
        register(ReservedWords.RMDIR, args -> new RemoveDirectoriesCommand().execute(args));
        register(ReservedWords.RENAME, args -> new RenameCommand().execute(args));
        register(ReservedWords.HELP, _ -> FunctionCommands.helpCommand());
        register(ReservedWords.NEW, FunctionCommands::newCommand);
        register(ReservedWords.SHOW, FunctionCommands::showCommand);
        register(ReservedWords.VERSION, _ -> FunctionCommands.versionCommand());
        register(ReservedWords.PRINT, FunctionCommands::printCommand);
        register(ReservedWords.MCD, FunctionCommands::MCDCommand);
        register(ReservedWords.EXIT, _ -> { System.exit(0); return CommandResult.Exit; });
        register(ReservedWords.CLEAR, _ -> { System.out.print(Ansi.CLEAR_SCREEN); return CommandResult.Success; });
        register(ReservedWords.WHEREAMI, _ -> { System.out.println(CurrentDirectory.get()); return CommandResult.Success; });
        register(ReservedWords.PAUSE, _ ->
        {
            try
            {
                ConsoleUtils.getch();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            return CommandResult.Success;
        });
        alias(ReservedWords.VER, ReservedWords.VERSION);
        alias(ReservedWords.CD, ReservedWords.CHDIR);
        alias(ReservedWords.CLS, ReservedWords.CLEAR);
        alias(ReservedWords.DEL, ReservedWords.DELETE);
        alias(ReservedWords.ERASE, ReservedWords.DELETE);
        alias(ReservedWords.MD, ReservedWords.MKDIR);
        alias(ReservedWords.RD, ReservedWords.RMDIR);
        alias(ReservedWords.REN, ReservedWords.RENAME);
    }

    private void register(ReservedWords word, Function<List<String>, CommandResult> fn)
    {
        commands.put(word, fn);
    }

    private void alias(ReservedWords alias, ReservedWords target)
    {
        commands.put(alias, commands.get(target));
    }

    public boolean executeCommand(String input)
    {
        if (input == null || input.isBlank())
            return true;

        String trimmed = input.trim();
        if (trimmed.contains("|") || trimmed.contains(">") || trimmed.contains("<"))
            return executePipeline(trimmed);

        ParsedCommand parsed = parseArgs(trimmed);

        if (isPathLike(parsed.command()))
        {
            try
            {
                Path cmdPath = CurrentDirectory.get().resolve(parsed.command()).normalize();
                if (Files.isDirectory(cmdPath))
                {
                    ErrorPrinter.setLastError("Error: '" + parsed.command() + "' is a directory");
                    return false;
                }
                if (Files.exists(cmdPath) && Files.isRegularFile(cmdPath) && FileSystemUtils.isExecutable(cmdPath))
                    return FileSystemUtils.executeExecutable(cmdPath, parsed.args());
                else
                {
                    ErrorPrinter.setLastError("Error: '" + parsed.command() + "' not found or not executable");
                    return false;
                }
            }
            catch (Exception e)
            {
                ErrorPrinter.setLastError("Error executing '" + parsed.command() + "': " + e.getMessage());
                return false;
            }
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean isWindows = os.contains("win");

        String lookup = isWindows ? parsed.command().toUpperCase(Locale.ROOT) : parsed.command();
        ReservedWords word = ReservedWords.fromString(lookup);
        Function<List<String>, CommandResult> cmd = commands.get(word);
        if (cmd != null)
        {
            CommandResult result = cmd.apply(parsed.args());
            if (result != CommandResult.Success)
            {
                ErrorPrinter.print(ErrorPrinter.getLastError().orElse(""));
                return false;
            }

            IO.print(OutputPrinter.getLastOutput().orElse(""));
            ErrorPrinter.setLastError(null);
            OutputPrinter.setLastOutput(null);
            ErrorPrinter.clearLastError();
            OutputPrinter.clearLastOutput();
            return true;
        }

        Optional<Path> pathCommand = findInPath(parsed.command());
        if (pathCommand.isPresent())
            return FileSystemUtils.executeExecutable(pathCommand.get(), parsed.args());

        ErrorPrinter.setLastError("Error: '" + parsed.command() + "' not found or not executable");
        System.err.println(ErrorPrinter.getLastError().orElse(""));
        return false;
    }

    private ParsedCommand parseArgs(String input)
    {
        String[] parts = input.trim().split("\\s+", 2);
        String cmd = parts[0];
        String argsStr = parts.length > 1 ? parts[1] : "";

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean isWindows = os.contains("win");

        if (isWindows && !isPathLike(cmd))
            cmd = cmd.toUpperCase(Locale.ROOT);

        List<String> args = splitQuotedArgs(argsStr);
        return new ParsedCommand(cmd, args);
    }


    private List<String> splitQuotedArgs(String inputStr)
    {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : inputStr.toCharArray())
        {
            if (c == '"')
            {
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes)
            {
                if (!current.isEmpty())
                {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            }
            else
                current.append(c);
        }
        if (!current.isEmpty())
            tokens.add(current.toString());
        return tokens;
    }

    private boolean isPathLike(String cmd)
    {
        if (cmd == null || cmd.isEmpty())
            return false;
        if (cmd.contains("/") || cmd.contains("\\"))
            return true;
        if (cmd.startsWith("./") || cmd.startsWith(".\\"))
            return true;
        if (cmd.startsWith("../") || cmd.startsWith("..\\"))
            return true;
        return cmd.length() >= 2 && cmd.charAt(1) == ':';
    }

    public static Map<ReservedWords, Function<List<String>, CommandResult>> getCommands()
    {
        return Collections.unmodifiableMap(commands);
    }

    private Optional<Path> findInPath(String command)
    {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null)
            return Optional.empty();
        String[] pathDirs = pathEnv.split(File.pathSeparator);
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        for (String dir : pathDirs)
        {
            Path candidate = Paths.get(dir, command);
            if (Files.exists(candidate) && FileSystemUtils.isExecutable(candidate))
                return Optional.of(candidate);
            if (isWindows)
            {
                if (!command.matches(".*\\.[a-zA-Z0-9]+$"))
                {
                    String[] extensions = { ".exe", ".com", ".bat", ".cmd" };
                    for (String ext : extensions)
                    {
                        Path candidateWithExt = Paths.get(dir, command + ext);
                        if (Files.exists(candidateWithExt) && FileSystemUtils.isExecutable(candidateWithExt))
                            return Optional.of(candidateWithExt);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean executePipeline(String commandLine)
    {
        try
        {
            List<String> segments = getStrings(commandLine);
            if (segments.isEmpty())
                return true;

            String first = segments.getFirst();
            Path inputFile = null;
            {
                List<String> tokens = splitQuotedArgs(first);
                List<String> cleaned = new ArrayList<>();
                for (int i = 0; i < tokens.size(); i++)
                {
                    String token = tokens.get(i);
                    if (token.equals("<") && i + 1 < tokens.size())
                    {
                        String fname = tokens.get(i + 1);
                        inputFile = CurrentDirectory.get().resolve(fname).normalize();
                        i++;
                    }
                    else
                        cleaned.add(token);
                }
                segments.set(0, String.join(" ", cleaned));
            }

            String last = segments.getLast();
            Path outputFile = null;
            boolean append = false;
            {
                List<String> tokens = splitQuotedArgs(last);
                List<String> cleaned = new ArrayList<>();
                for (int i = 0; i < tokens.size(); i++)
                {
                    String token = tokens.get(i);
                    if ((token.equals(">") || token.equals(">>")) && i + 1 < tokens.size())
                    {
                        outputFile = CurrentDirectory.get().resolve(tokens.get(i + 1)).normalize();
                        append = token.equals(">>");
                        i++;
                    }
                    else
                        cleaned.add(token);
                }
                segments.set(segments.size() - 1, String.join(" ", cleaned));
            }

            byte[] data;
            if (inputFile != null)
                data = Files.readAllBytes(inputFile);
            else
                data = new byte[0];

            for (String segCmd : segments)
            {
                String seg = segCmd.trim();
                if (seg.isEmpty())
                    continue;

                ParsedCommand pc = parseArgs(seg);
                byte[] output;

                if (!isPathLike(pc.command()))
                {
                    String cmdStr = pc.command().toUpperCase(Locale.ROOT);
                    Function<List<String>, CommandResult> fn = commands.get(ReservedWords.fromString(cmdStr));
                    if (fn != null)
                    {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        PrintStream originalOut = System.out;
                        PrintStream ps = new PrintStream(buffer);
                        System.setOut(ps);
                        CommandResult res;
                        try
                        {
                            res = fn.apply(pc.args());
                        }
                        finally
                        {
                            ps.flush();
                            System.setOut(originalOut);
                        }
                        output = buffer.toByteArray();

                        String op = OutputPrinter.getLastOutput().orElse("");
                        if (!op.isEmpty())
                        {
                            byte[] opBytes = op.getBytes();
                            byte[] combined = new byte[output.length + opBytes.length];
                            System.arraycopy(output, 0, combined, 0, output.length);
                            System.arraycopy(opBytes, 0, combined, output.length, opBytes.length);
                            output = combined;
                        }
                        OutputPrinter.clearLastOutput();

                        if (res != CommandResult.Success)
                            return false;
                    }
                    else
                    {
                        Optional<Path> pathCmd = findInPath(pc.command());
                        if (pathCmd.isPresent())
                            output = runExternal(pathCmd.get().toString(), pc.args(), data);
                        else
                            output = runExternal(pc.command(), pc.args(), data);
                    }
                }
                else
                {
                    Path abs = CurrentDirectory.get().resolve(pc.command()).normalize();
                    output = runExternal(abs.toString(), pc.args(), data);
                }

                data = output;
            }

            if (outputFile != null)
            {
                if (append)
                    Files.write(outputFile, data, Files.exists(outputFile) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
                else
                    Files.write(outputFile, data);
            }
            else
            {
                if (data.length > 0)
                    System.out.write(data);
            }

            return true;
        }
        catch (IOException e)
        {
            ErrorPrinter.setLastError("Pipeline execution failed: " + e.getMessage());
            return false;
        }
    }

    private static List<String> getStrings(String commandLine)
    {
        List<String> segments = new ArrayList<>();
        StringBuilder seg = new StringBuilder();
        boolean inQuotes = false;
        for (char c : commandLine.toCharArray())
        {
            if (c == '"')
            {
                inQuotes = !inQuotes;
                seg.append(c);
                continue;
            }
            if (c == '|' && !inQuotes)
            {
                segments.add(seg.toString().trim());
                seg.setLength(0);
            }
            else
                seg.append(c);
        }
        segments.add(seg.toString().trim());
        return segments;
    }

    private byte[] runExternal(String command, List<String> args, byte[] input)
    {
        try
        {
            List<String> cmdLine = new ArrayList<>();
            cmdLine.add(command);
            cmdLine.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmdLine);
            pb.directory(CurrentDirectory.get().toFile());
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            Process process = pb.start();
            if (input != null && input.length > 0)
            {
                OutputStream os = process.getOutputStream();
                os.write(input);
                os.close();
            }
            else
                process.getOutputStream().close();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thread outThread = new Thread(() ->
            {
                try
                {
                    InputStream is = process.getInputStream();
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = is.read(buf)) != -1)
                        buffer.write(buf, 0, len);
                }
                catch (IOException _) {}
            });
            outThread.start();
            Thread errThread = new Thread(() ->
            {
                try
                {
                    InputStream es = process.getErrorStream();
                    byte[] buf2 = new byte[1024];
                    int len2;
                    while ((len2 = es.read(buf2)) != -1)
                        System.err.write(buf2, 0, len2);
                }
                catch (IOException _) {}
            });
            errThread.start();
            int exitCode = process.waitFor();
            outThread.join();
            errThread.join();
            if (exitCode != 0)
                ErrorPrinter.setLastError("Error: external command exited with code " + exitCode);
            return buffer.toByteArray();
        }
        catch (Exception e)
        {
            ErrorPrinter.setLastError("Execution failed: " + e.getMessage());
            return new byte[0];
        }
    }
}
