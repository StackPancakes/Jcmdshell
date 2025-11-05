package xyz.stackpancakes.shell.util;

/**
 * Simple ANSI escape helper class.  Supports coloring text by foreground and
 * background and resetting the terminal.  Used throughout the shell for
 * user feedback and theming.
 */
public final class Ansi
{
    private Ansi() {}

    public enum Foreground
    {
        BLACK(30),
        RED(31),
        GREEN(32),
        YELLOW(33),
        BLUE(34),
        MAGENTA(35),
        CYAN(36),
        WHITE(37),
        BRIGHT_BLACK(90),
        BRIGHT_RED(91),
        BRIGHT_GREEN(92),
        BRIGHT_YELLOW(93),
        BRIGHT_BLUE(94),
        BRIGHT_MAGENTA(95),
        BRIGHT_CYAN(96),
        BRIGHT_WHITE(97);
        private final int code;
        Foreground(int code)
        {
            this.code = code;
        }
        public int code()
        {
            return code;
        }
    }
    public enum Background
    {
        BLACK(40),
        RED(41),
        GREEN(42),
        YELLOW(43),
        BLUE(44),
        MAGENTA(45),
        CYAN(46),
        WHITE(47),
        BRIGHT_BLACK(100),
        BRIGHT_RED(101),
        BRIGHT_GREEN(102),
        BRIGHT_YELLOW(103),
        BRIGHT_BLUE(104),
        BRIGHT_MAGENTA(105),
        BRIGHT_CYAN(106),
        BRIGHT_WHITE(107);
        private final int code;
        Background(int code)
        {
            this.code = code;
        }
        public int code()
        {
            return code;
        }
    }
    public static final String RESET = "\u001B[0m";
    public static final String CLEAR_SCREEN = "\u001B[2J\u001B[H";
    public static String foreground(Foreground fg)
    {
        return "\u001B[" + fg.code() + "m";
    }
    public static String background(Background bg)
    {
        return "\u001B[" + bg.code() + "m";
    }
    public static String withForeground(String str, Foreground fg)
    {
        return foreground(fg) + str + RESET;
    }
    public static String withBackground(String str, Background bg)
    {
        return background(bg) + str + RESET;
    }
}