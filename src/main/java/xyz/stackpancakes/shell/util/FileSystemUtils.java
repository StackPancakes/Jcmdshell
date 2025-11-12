package xyz.stackpancakes.shell.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class FileSystemUtils
{
    private static final AtomicReference<Process> currentProcess = new AtomicReference<>();
    private static final Pattern ANSI_PATTERN = Pattern.compile(
            "\u001B\\[[0-9;?]*[ -/]*[@-~]|\u009B[0-9;?]*[ -/]*[@-~]"
    );

    public static String getHomeDirectory()
    {
        return System.getProperty("user.home");
    }

    public static boolean isExecutable(Path entry)
    {
        if (entry == null || !Files.exists(entry))
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
        catch (Exception _)
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
        builder.redirectErrorStream(true);

        setupColorFriendlyEnv(builder);

        try
        {
            Process process = builder.start();
            currentProcess.set(process);

            ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
            boolean ansiOk = supportsAnsi();
            InputStream in = process.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            Charset cs = Charset.defaultCharset();

            while ((n = in.read(buf)) != -1)
            {
                outputCapture.write(buf, 0, n);

                if (ansiOk)
                {
                    System.out.write(buf, 0, n);
                }
                else
                {
                    String chunk = new String(buf, 0, n, cs);
                    String cleaned = stripAnsi(chunk);
                    System.out.print(cleaned);
                }
                System.out.flush();
            }

            int exitCode = process.waitFor();

            String captured = outputCapture.toString();
            OutputPrinter.setLastOutput(captured);
            if (exitCode != 0)
                ErrorPrinter.setLastError(captured);
            else
                ErrorPrinter.setLastError("");

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

    static boolean returnCode(int exitCode, int consoleWidth)
    {
        boolean isSuccess = exitCode == 0;
        int spaces = consoleWidth - 2;
        if (spaces < 0)
            spaces = 0;

        if (isSuccess)
            System.out.println(" ".repeat(spaces) + Ansi.withForeground(":)", Ansi.Foreground.GREEN));
        else
            System.out.println(" ".repeat(spaces) + Ansi.withForeground(":(", Ansi.Foreground.RED));

        return isSuccess;
    }

    private static int getConsoleWidth()
    {
        int width = 80;

        try
        {
            int w = TerminalShare.getSharedTerminal().getWidth();
            if (w > 0)
                width = w;
        }
        catch (Exception _)
        {}

        return width;
    }

    private static boolean supportsAnsi()
    {
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null && !noColor.isEmpty())
            return false;

        try
        {
            return TerminalShare.getSharedTerminal() != null;
        }
        catch (Exception _)
        {
            return System.console() != null;
        }
    }

    private static void setupColorFriendlyEnv(ProcessBuilder builder)
    {
        Map<String, String> env = builder.environment();

        String term = env.get("TERM");
        if (term == null || term.isEmpty() || "dumb".equalsIgnoreCase(term))
            env.put("TERM", "xterm-256color");

        env.putIfAbsent("CLICOLOR_FORCE", "1");
        env.putIfAbsent("FORCE_COLOR", "1");
    }

    private static String stripAnsi(String s)
    {
        if (s == null || s.isEmpty())
            return s;

        return ANSI_PATTERN.matcher(s).replaceAll("");
    }
}
