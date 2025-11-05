package xyz.stackpancakes.shell.core;

import java.util.Locale;

/**
 * Enumeration of reserved command names.  Each constant has an associated
 * informational string describing its purpose.  Methods are provided to look
 * up a reserved word from a string and obtain that description.
 */
public enum ReservedWords
{
    CHDIR,
    CD,
    CLEAR,
    CLS,
    COPY,
    DELETE,
    DEL,
    DIR,
    ERASE,
    EXIT,
    HELP,
    MCD,
    MKDIR,
    MD,
    NEW,
    PAUSE,
    PRINT,
    RENAME,
    RMDIR,
    RD,
    REN,
    SHOW,
    VERSION,
    VER,
    WHEREAMI,
    UNKNOWN;

    public static ReservedWords fromString(String str)
    {
        if (str == null)
            return UNKNOWN;
        try
        {
            return ReservedWords.valueOf(str.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex)
        {
            return UNKNOWN;
        }
    }

    public String info()
    {
        return switch (this)
        {
            case EXIT -> "Exit from the shell";
            case CLEAR, CLS -> "Clear the screen";
            case DIR -> "List files and directories";
            case VERSION, VER -> "JCmdShell Shell version";
            case PRINT -> "Display messages";
            case HELP -> "Show help";
            case MCD -> "Create and change to the new directory";
            case MKDIR, MD -> "Create Directories";
            case RMDIR, RD -> "Remove Directories";
            case RENAME, REN -> "Replace a file or files";
            case COPY -> "Copy files";
            case NEW -> "Create a file";
            case SHOW -> "Outputs the file";
            case DELETE, DEL, ERASE -> "Removes the file";
            case CHDIR, CD -> "Change current directories";
            case WHEREAMI -> "Show the current directories";
            case PAUSE -> "Delays the shell until you press any keys";
            default -> "UNKNOWN";
        };
    }
}