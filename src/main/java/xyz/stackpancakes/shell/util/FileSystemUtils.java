package xyz.stackpancakes.shell.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
                return fileName.endsWith(".exe")
                        || fileName.endsWith(".bat")
                        || fileName.endsWith(".com")
                        || fileName.endsWith(".cmd");
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

        try
        {
            Process process = builder.start();
            currentProcess.set(process);

            ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
            InputStream in = process.getInputStream();
            byte[] buf = new byte[8192];
            int n;

            while ((n = in.read(buf)) != -1)
            {
                outputCapture.write(buf, 0, n);
                System.out.write(buf, 0, n);
                System.out.flush();
            }

            int exitCode = process.waitFor();

            String captured = outputCapture.toString();
            OutputPrinter.setLastOutput(captured);
            if (exitCode != 0)
                ErrorPrinter.setLastError("Error: external command exited with code " + exitCode);
            else
                ErrorPrinter.setLastError("");

            return exitCode == 0;
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
}