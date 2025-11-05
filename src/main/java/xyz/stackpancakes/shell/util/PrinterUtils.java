package xyz.stackpancakes.shell.util;

import java.io.PrintStream;

public class PrinterUtils
{
    public static void printFormatted(String message, PrintStream out)
    {
        String cleaned = message.replaceAll("\u001B\\[[0-9;]*[A-Za-z]", "");
        int col = 0;
        int lastLineCol = 0;
        for (int i = 0; i < cleaned.length(); i++)
        {
            char c = cleaned.charAt(i);
            if (c == '\n' || c == '\r')
                col = 0;
            else if (c == '\t')
                col += 8 - (col % 8);
            else
                ++col;
            lastLineCol = col;
        }
        PrintStream output = getPrintStream(message, lastLineCol, out);
        output.println();
    }
    private static PrintStream getPrintStream(String message, int lastLineCol, PrintStream out)
    {
        int consoleWidth = getConsoleWidth();
        if (consoleWidth <= 0)
            consoleWidth = 80;
        int cursorCol = lastLineCol % consoleWidth;
        int emoticonWidth = 2;
        int spacesNeeded = consoleWidth - cursorCol - emoticonWidth;
        String padding;
        if (spacesNeeded > 0)
            padding = " ".repeat(spacesNeeded);
        else
            padding = "\n" + " ".repeat(Math.max(0, consoleWidth - emoticonWidth));
        out.print(message);
        out.print(padding);
        if (out == System.err)
            out.print(Ansi.withForeground(":(", Ansi.Foreground.RED));
        else
            out.print(Ansi.withForeground(":)", Ansi.Foreground.GREEN));
        return out;
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
}
