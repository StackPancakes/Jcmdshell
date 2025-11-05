package xyz.stackpancakes.shell.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ensures that the shell does not start in the root directory.  If the
 * working directory is the file system root this method attempts to change
 * into the user's home directory.
 */
public final class StartupDirectory
{
    private StartupDirectory() {}
    public static void ensureStartupDirectory()
    {
        try
        {
            Path cwd = Paths.get("").toAbsolutePath();
            Path root = cwd.getRoot();
            if (cwd.equals(root))
            {
                String home = FileSystemUtils.getHomeDirectory();
                if (home != null && !home.isEmpty() && !home.equals("."))
                {
                    Path homePath = Paths.get(home);
                    if (!homePath.isAbsolute())
                        homePath = homePath.toAbsolutePath();
                    homePath = homePath.toRealPath();
                    if (Files.exists(homePath) && Files.isDirectory(homePath))
                        System.setProperty("user.dir", homePath.toString());
                }
            }
        }
        catch (Exception e)
        {
            ErrorPrinter.print(
                    Ansi.withForeground("Startup", Ansi.Foreground.GREEN)
                            + ": error while initializing working directory: "
                            + e.getMessage()
            );
        }
    }
}