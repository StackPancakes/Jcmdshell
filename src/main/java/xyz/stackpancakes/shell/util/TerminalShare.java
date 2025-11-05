package xyz.stackpancakes.shell.util;

import org.jline.terminal.Terminal;
import xyz.stackpancakes.Main;

/**
 * Provides a shared {@link Terminal} instance accessible from different
 * components.  This allows code that needs to query terminal properties to
 * obtain the same terminal instance that was created in {@link Main#main}.
 */
public final class TerminalShare
{
    private static final Object lock = new Object();
    private static Terminal sharedTerminal;
    private TerminalShare() {}
    public static void setSharedTerminal(Terminal terminal)
    {
        synchronized (lock)
        {
            sharedTerminal = terminal;
        }
    }
    public static Terminal getSharedTerminal()
    {
        synchronized (lock)
        {
            return sharedTerminal;
        }
    }
}