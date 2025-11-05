package xyz.stackpancakes.shell.util;

import java.util.Optional;

import static xyz.stackpancakes.shell.util.PrinterUtils.printFormatted;

public final class ErrorPrinter
{
    private static final Object lock = new Object();
    private static String lastError;

    public static void setLastError(String msg)
    {
        synchronized (lock)
        {
            lastError = msg;
        }
    }

    public static Optional<String> getLastError()
    {
        synchronized (lock)
        {
            return Optional.ofNullable(lastError);
        }
    }

    public static void clearLastError()
    {
        synchronized (lock)
        {
            lastError = null;
        }
    }

    public static void print(String message)
    {
        printFormatted(message, System.err);
    }
}