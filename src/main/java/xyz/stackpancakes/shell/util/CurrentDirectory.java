package xyz.stackpancakes.shell.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Global holder for the current working directory.  Because Java does not
 * expose an API to change the process working directory, this class keeps
 * track of it and updates the {@code user.dir} system property for external
 * commands that rely on it.
 */
public class CurrentDirectory
{
    private static Path currentDirectory = Paths.get(System.getProperty("user.dir"));
    public static Path get()
    {
        return currentDirectory;
    }
    public static void set(Path path)
    {
        currentDirectory = path;
        System.setProperty("user.dir", path.toString());
    }
}