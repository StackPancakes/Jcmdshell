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

        Process process = null;

        ByteArrayOutputStream stdoutCapture = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrCapture = new ByteArrayOutputStream();
        Charset cs = Charset.defaultCharset();

        try
        {
            process = builder.start();
            currentProcess.set(process);

            Thread outThread = getOutThread(process, stdoutCapture, cs);

            Thread errThread = getThread(process, stderrCapture, cs);

            outThread.start();
            errThread.start();

            int exitCode = process.waitFor();

            outThread.join();
            errThread.join();

            String stdout = stdoutCapture.toString(cs);
            String stderr = stderrCapture.toString(cs);
            String captured = stdout + stderr;

            OutputPrinter.setLastOutput(captured);
            if (exitCode != 0)
            {
                if (!stderr.isEmpty())
                    ErrorPrinter.setLastError(stderr);
                else
                    ErrorPrinter.setLastError(captured);
            }
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
            if (process != null && process.isAlive())
                process.destroy();
        }
    }

    private static Thread getOutThread(Process process, ByteArrayOutputStream stdoutCapture, Charset cs)
    {
        return new Thread(() ->
        {
            try
            {
                InputStream in = process.getInputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1)
                {
                    stdoutCapture.write(buf, 0, n);
                    System.out.print(new String(buf, 0, n, cs));
                    System.out.flush();
                }
            }
            catch (IOException _)
            {
            }
        });
    }

    private static Thread getThread(Process process, ByteArrayOutputStream stderrCapture, Charset cs)
    {
        return new Thread(() ->
        {
            try
            {
                InputStream err = process.getErrorStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = err.read(buf)) != -1)
                {
                    stderrCapture.write(buf, 0, n);
                    System.err.print(new String(buf, 0, n, cs));
                    System.err.flush();
                }
            }
            catch (IOException _)
            {
            }
        });
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
        {
        }

        return width;
    }
}
