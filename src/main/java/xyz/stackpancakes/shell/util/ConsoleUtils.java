package xyz.stackpancakes.shell.util;

import org.jline.terminal.Terminal;

/**
 * Utilities for interacting with the console.  Provides methods to pause
 * execution until the user presses a key.
 */
public final class ConsoleUtils
{
    private ConsoleUtils() {}
    public static void getch() throws Exception
    {
        Terminal terminal = TerminalShare.getSharedTerminal();
        IO.print("Please enter any key to continue...");
        int _ = terminal.reader().read();
        IO.println('\n');
    }
}