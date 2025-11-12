package xyz.stackpancakes.shell.util;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);

        try
        {
            Process process = builder.start();
            currentProcess.set(process);

            ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
            ByteArrayOutputStream errorCapture = new ByteArrayOutputStream();

            boolean ansiOk = supportsAnsi();

            Thread outThread = getOutThread(process.getInputStream(), System.out, outputCapture, ansiOk);
            outThread.start();
            Thread errThread = getOutThread(process.getErrorStream(), System.err, errorCapture, ansiOk);
            errThread.start();

            int exitCode = process.waitFor();

            try
            {
                outThread.join(200);
                errThread.join(200);
            }
            catch (InterruptedException _)
            {
            }

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

    static boolean returnCode(int exitCode, int consoleWidth)
    {
        boolean isSuccess = exitCode == 0;
        if (isSuccess)
            System.out.println(" ".repeat(consoleWidth - 2) + Ansi.withForeground(":)", Ansi.Foreground.GREEN));
        else
            System.out.println(" ".repeat(consoleWidth - 2) + Ansi.withForeground(":(", Ansi.Foreground.RED));
        return isSuccess;
    }

    private static Thread getOutThread(InputStream in, PrintStream console, ByteArrayOutputStream capture, boolean ansiOk)
    {
        return new Thread(() ->
        {
            byte[] buf = new byte[8192];
            int n;
            Charset cs = Charset.defaultCharset();
            try
            {
                while ((n = in.read(buf)) != -1)
                {
                    capture.write(buf, 0, n);

                    if (console != null)
                    {
                        if (ansiOk)
                        {
                            console.write(buf, 0, n);
                        }
                        else
                        {
                            String chunk = new String(buf, 0, n, cs);
                            String cleaned = stripAnsi(chunk);
                            console.print(cleaned);
                        }
                        console.flush();
                    }
                }
            }
            catch (IOException _)
            {
            }
        }, "io-out");
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
        String term = System.getenv("TERM");
        String noColor = System.getenv("NO_COLOR");
        String wt = System.getenv("WT_SESSION");
        String ansicon = System.getenv("ANSICON");
        String conemu = System.getenv("ConEmuANSI");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if ("dumb".equalsIgnoreCase(term))
            return false;

        if (noColor != null && !noColor.isEmpty())
            return false;

        if (!os.contains("win"))
            return term != null && !term.isEmpty();

        if (wt != null && !wt.isEmpty())
            return true;

        if (ansicon != null && !ansicon.isEmpty())
            return true;

        return "ON".equalsIgnoreCase(conemu);
    }


    private static String stripAnsi(String s)
    {
        if (s == null || s.isEmpty())
            return s;
        return ANSI_PATTERN.matcher(s).replaceAll("");
    }
}
