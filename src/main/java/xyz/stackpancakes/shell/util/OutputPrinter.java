package xyz.stackpancakes.shell.util;

import java.util.Optional;

public final class OutputPrinter
{
    private static final Object lock = new Object();
    private static String lastOutput;

    public static void setLastOutput(String msg)
    {
        synchronized (lock)
        {
            lastOutput = msg;
        }
    }

    public static Optional<String> getLastOutput()
    {
        synchronized (lock)
        {
            return Optional.ofNullable(lastOutput);
        }
    }

    public static void clearLastOutput()
    {
        synchronized (lock)
        {
            lastOutput = null;
        }
    }

    public String getString()
    {
        return lastOutput;
    }
}