package xyz.stackpancakes.shell.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class FileSystemUtils
{
    private static final AtomicReference<Process> currentProcess = new AtomicReference<>();

    public static String getHomeDirectory()
    {
        return System.getProperty("user.home");
    }

    public static boolean isExecutable(Path entry)
    {
        if (entry == null || !Files.isRegularFile(entry))
            return false;
        try
        {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            boolean isWindows = os.contains("win");
            if (isWindows)
            {
                String fileName = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                return fileName.endsWith(".exe") || fileName.endsWith(".bat") || fileName.endsWith(".com") || fileName.endsWith(".cmd");
            }
            else
                return Files.isExecutable(entry);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static void interruptCurrentProcess()
    {
        Process process = currentProcess.get();
        if (process != null && process.isAlive())
        {
            process.destroy();
            if (process.isAlive())
                process.destroyForcibly();
        }
    }

    public static boolean executeExecutable(Path path, List<String> args)
    {
        List<String> command = new ArrayList<>();
        command.add(path.toAbsolutePath().toString());
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(CurrentDirectory.get().toFile());
        builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        try
        {
            Process process = builder.start();
            currentProcess.set(process);

            ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
            ByteArrayOutputStream errorCapture = new ByteArrayOutputStream();

            Thread inThread = getInThread(process);
            inThread.start();
            Thread outThread = getOutThread(process.getInputStream(), System.out, outputCapture, true);
            outThread.start();
            Thread errThread = getOutThread(process.getErrorStream(), System.err, errorCapture, true);
            errThread.start();

            int exitCode = process.waitFor();

            try
            {
                process.getOutputStream().close();
            }
            catch (IOException _) {}
            try
            {
                inThread.interrupt();
                inThread.join(200);
            }
            catch (InterruptedException _) {}
            try
            {
                outThread.join(200);
                errThread.join(200);
            }
            catch (InterruptedException _) {}

            OutputPrinter.setLastOutput(outputCapture.toString());
            ErrorPrinter.setLastError(errorCapture.toString());

            return returnCode(exitCode, getConsoleWidth());
        }
        catch (IOException | InterruptedException e)
        {
            ErrorPrinter.setLastError("Execution failed: " + e.getMessage());
            return false;
        }
        finally
        {
            currentProcess.set(null);
        }
    }

    public static boolean executeShellCommand(String command)
    {
        try
        {
            List<ParsedCommand> commands = parseCommandLine(command);
            if (commands.isEmpty())
            {
                ErrorPrinter.setLastError("Empty command");
                return false;
            }

            List<Process> processes = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();

            ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
            ByteArrayOutputStream errorCapture = new ByteArrayOutputStream();

            boolean hasRedirectOrPipe = commands.size() > 1 ||
                    commands.stream().anyMatch(cmd -> cmd.inputRedirect != null || cmd.outputRedirect != null);

            try
            {
                for (int i = 0; i < commands.size(); i++)
                {
                    ParsedCommand parsed = commands.get(i);
                    ProcessBuilder builder = new ProcessBuilder(parsed.command);
                    builder.directory(CurrentDirectory.get().toFile());

                    if (parsed.inputRedirect != null)
                        builder.redirectInput(ProcessBuilder.Redirect.from(new File(parsed.inputRedirect)));
                    else if (i > 0)
                        builder.redirectInput(ProcessBuilder.Redirect.PIPE)
                                ;
                    else
                        builder.redirectInput(ProcessBuilder.Redirect.INHERIT)
                                ;

                    if (parsed.outputRedirect != null)
                    {
                        if (parsed.appendRedirect)
                            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(parsed.outputRedirect)));
                        else
                            builder.redirectOutput(ProcessBuilder.Redirect.to(new File(parsed.outputRedirect)));
                    }
                    else
                        builder.redirectOutput(ProcessBuilder.Redirect.PIPE)
                                ;

                    builder.redirectError(ProcessBuilder.Redirect.PIPE);

                    Process process = builder.start();
                    processes.add(process);
                }

                Process lastProcess = processes.getLast();
                currentProcess.set(lastProcess);

                for (int i = 0; i + 1 < processes.size(); i++)
                {
                    Process src = processes.get(i);
                    Process dst = processes.get(i + 1);
                    Thread pump = pipe(src.getInputStream(), dst.getOutputStream());
                    pump.start();
                    threads.add(pump);
                }

                for (int i = 0; i < processes.size(); i++)
                {
                    Process process = processes.get(i);
                    boolean isLastProcess = (i == processes.size() - 1);
                    ParsedCommand parsed = commands.get(i);

                    boolean shouldPrintOutput = isLastProcess && parsed.outputRedirect == null;
                    boolean shouldPrintError = true;

                    if (shouldPrintOutput)
                    {
                        Thread outThread = getOutThread(process.getInputStream(), System.out, outputCapture, true);
                        outThread.start();
                        threads.add(outThread);
                    }

                    Thread errThread = getOutThread(process.getErrorStream(), System.err, errorCapture, shouldPrintError);
                    errThread.start();
                    threads.add(errThread);
                }

                if (commands.getFirst().inputRedirect == null)
                {
                    Thread inThread = getInThread(processes.getFirst());
                    inThread.start();
                    threads.add(inThread);
                }

                int exitCode = 0;
                for (Process process : processes)
                    exitCode = process.waitFor();

                for (Thread thread : threads)
                {
                    try
                    {
                        thread.join(200);
                    }
                    catch (InterruptedException _) {}
                }

                OutputPrinter.setLastOutput(outputCapture.toString());
                ErrorPrinter.setLastError(errorCapture.toString());

                return returnCode(exitCode, getConsoleWidth(), !hasRedirectOrPipe);
            }
            finally
            {
                for (Process process : processes)
                    if (process.isAlive())
                        process.destroy();
            }
        }
        catch (Exception e)
        {
            ErrorPrinter.setLastError("Shell execution failed: " + e.getMessage());
            return false;
        }
        finally
        {
            currentProcess.set(null);
        }
    }

    static boolean returnCode(int exitCode, int consoleWidth)
    {
        return returnCode(exitCode, consoleWidth, true);
    }

    static boolean returnCode(int exitCode, int consoleWidth, boolean showEmoticon)
    {
        boolean isSuccess = exitCode == 0;
        if (showEmoticon)
        {
            if (isSuccess)
                System.out.println(" ".repeat(consoleWidth - 2) + Ansi.withForeground(":)", Ansi.Foreground.GREEN));
            else
                System.out.println(" ".repeat(consoleWidth - 2) + Ansi.withForeground(":(", Ansi.Foreground.RED));
        }
        return isSuccess;
    }

    private static Thread pipe(InputStream in, OutputStream out)
    {
        return new Thread(() ->
        {
            byte[] buf = new byte[8192];
            int n;
            try
            {
                while ((n = in.read(buf)) != -1)
                {
                    out.write(buf, 0, n);
                    out.flush();
                }
            }
            catch (IOException _)
            {
            }
            finally
            {
                try
                {
                    out.close();
                }
                catch (IOException _)
                {
                }
            }
        }, "io-pipe");
    }

    private static Thread getOutThread(InputStream in, PrintStream console, ByteArrayOutputStream capture, boolean toConsole)
    {
        return new Thread(() ->
        {
            byte[] buf = new byte[8192];
            int n;
            try
            {
                while ((n = in.read(buf)) != -1)
                {
                    capture.write(buf, 0, n);
                    if (toConsole && console != null)
                    {
                        console.write(buf, 0, n);
                        console.flush();
                    }
                }
            }
            catch (IOException _)
            {
            }
        }, "io-out");
    }

    private static Thread getInThread(Process process)
    {
        return new Thread(() ->
        {
            try
            {
                OutputStream procIn = process.getOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while (process.isAlive() && (n = System.in.read(buf)) != -1)
                {
                    procIn.write(buf, 0, n);
                    procIn.flush();
                }
                try
                {
                    procIn.close();
                }
                catch (IOException _)
                {
                }
            }
            catch (IOException _)
            {
            }
        }, "io-in");
    }

    private static int getConsoleWidth()
    {
        int width = 0;
        try
        {
            width = TerminalShare.getSharedTerminal().getWidth();
            return width > 0 ? width : 80;
        }
        catch (Exception _) {}
        return width;
    }

    private static class ParsedCommand
    {
        List<String> command;
        String inputRedirect;
        String outputRedirect;
        boolean appendRedirect;

        ParsedCommand(List<String> command)
        {
            this.command = command;
        }
    }

    private static List<ParsedCommand> parseCommandLine(String commandLine)
    {
        List<ParsedCommand> commands = new ArrayList<>();
        String[] pipeParts = splitRespectingQuotes(commandLine);

        for (String pipePart : pipeParts)
        {
            List<String> tokens = tokenizeCommand(pipePart.trim());
            if (tokens.isEmpty())
                continue;

            ParsedCommand parsed = new ParsedCommand(new ArrayList<>());

            for (int i = 0; i < tokens.size(); i++)
            {
                String token = tokens.get(i);

                if (token.equals("<") && i + 1 < tokens.size())
                    parsed.inputRedirect = tokens.get(++i);
                else if ((token.equals(">") || token.equals(">>")) && i + 1 < tokens.size())
                {
                    parsed.outputRedirect = tokens.get(++i);
                    parsed.appendRedirect = token.equals(">>");
                }
                else
                    parsed.command.add(token);
            }

            commands.add(parsed);
        }

        return commands;
    }

    private static List<String> tokenizeCommand(String command)
    {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < command.length(); i++)
        {
            char c = command.charAt(i);

            if (escaped)
            {
                current.append(c);
                escaped = false;
            }
            else if (c == '^' && (inDoubleQuotes || !inSingleQuotes))
                escaped = true;
            else if (c == '"' && !inSingleQuotes)
                inDoubleQuotes = !inDoubleQuotes;
            else if (c == '\'' && !inDoubleQuotes)
                inSingleQuotes = !inSingleQuotes;
            else if (Character.isWhitespace(c) && !inDoubleQuotes && !inSingleQuotes)
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

    private static String[] splitRespectingQuotes(String input)
    {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++)
        {
            char c = input.charAt(i);

            if (escaped)
            {
                current.append(c);
                escaped = false;
            }
            else if (c == '^' && (inDoubleQuotes || !inSingleQuotes))
                escaped = true;
            else if (c == '"' && !inSingleQuotes)
            {
                inDoubleQuotes = !inDoubleQuotes;
                current.append(c);
            }
            else if (c == '\'' && !inDoubleQuotes)
            {
                inSingleQuotes = !inSingleQuotes;
                current.append(c);
            }
            else if (c == '|' && !inDoubleQuotes && !inSingleQuotes)
            {
                parts.add(current.toString().trim());
                current.setLength(0);
            }
            else
                current.append(c);
        }

        parts.add(current.toString().trim());
        return parts.toArray(new String[0]);
    }
}
