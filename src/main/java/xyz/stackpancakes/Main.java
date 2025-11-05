package xyz.stackpancakes;

import org.jline.builtins.Completers;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import xyz.stackpancakes.shell.core.CommandResult;
import xyz.stackpancakes.shell.core.ReservedWords;
import xyz.stackpancakes.shell.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public final class Main
{
    private Main()
    {
    }

    private static Completer createCommandCompleter(Map<ReservedWords, ?> commands)
    {
        return (_, parsedLine, candidates) ->
        {
            String currentWord = parsedLine.word();
            if (currentWord == null || currentWord.isEmpty())
                return;

            boolean toLower = Character.isLowerCase(currentWord.charAt(0));

            for (ReservedWords word : commands.keySet())
            {
                String name = word.name();
                String transformedName = toLower ? name.toLowerCase() : name.toUpperCase();
                String comparedWord = toLower ? currentWord.toLowerCase() : currentWord.toUpperCase();

                if (transformedName.startsWith(comparedWord))
                    candidates.add(new Candidate(
                            transformedName,
                            transformedName,
                            null,
                            word.info(),
                            null,
                            null,
                            false
                    ));
            }
        };
    }

    private static Completer createCustomFileCompleter()
    {
        Completer originalFileCompleter = new Completers.FileNameCompleter();

        return (reader, parsedLine, candidates) ->
        {
            List<Candidate> temp = new ArrayList<>();
            originalFileCompleter.complete(reader, parsedLine, temp);

            for (Candidate c : temp)
            {
                String value = c.value();

                if (value != null && !value.isEmpty())
                {
                    Path p = Paths.get(value);
                    if (!p.isAbsolute())
                        p = CurrentDirectory.get().resolve(p).normalize();

                    if (Files.isDirectory(p))
                    {
                        String displ = Ansi.withBackground(
                                Ansi.withForeground(c.value(), Ansi.Foreground.WHITE),
                                Ansi.Background.BLUE
                        );

                        candidates.add(new Candidate(
                                c.value(),
                                displ,
                                c.group(),
                                "Directory",
                                c.suffix(),
                                c.key(),
                                c.complete()
                        ));

                        continue;
                    }

                    if (FileSystemUtils.isExecutable(p))
                    {
                        String displ = Ansi.withForeground(c.value(), Ansi.Foreground.GREEN);

                        candidates.add(new Candidate(
                                c.value(),
                                displ,
                                c.group(),
                                "Executable",
                                c.suffix(),
                                c.key(),
                                c.complete()
                        ));

                        continue;
                    }
                }

                candidates.add(c);
            }
        };
    }

    private static Completer createPathExecutableCompleter()
    {
        return (_, parsedLine, candidates) ->
        {
            String pathEnv = System.getenv("PATH");
            if (pathEnv == null || pathEnv.isEmpty())
                return;

            String currentWord = parsedLine.word();
            if (currentWord == null || currentWord.isEmpty())
                return;

            String[] pathDirs = pathEnv.split(File.pathSeparator);
            Set<String> executables = new HashSet<>();

            for (String pathDir : pathDirs)
            {
                Path dirPath = Paths.get(pathDir);
                if (!Files.isDirectory(dirPath))
                    continue;

                try (Stream<Path> stream = Files.list(dirPath))
                {
                    stream.filter(Files::isRegularFile)
                            .filter(FileSystemUtils::isExecutable)
                            .map(p -> p.getFileName().toString())
                            .filter(name -> name.startsWith(currentWord))
                            .forEach(executables::add);
                }
                catch (IOException ignored)
                {
                }
            }

            for (String exe : executables)
                candidates.add(new Candidate(
                        exe,
                        Ansi.withForeground(exe, Ansi.Foreground.GREEN),
                        null,
                        "PATH executable",
                        null,
                        null,
                        false
                ));
        };
    }

    private static Completer createFallbackCompleter(Map<ReservedWords, ?> commands)
    {
        Completer commandCompleter = createCommandCompleter(commands);
        Completer fileCompleter = createCustomFileCompleter();
        Completer pathCompleter = createPathExecutableCompleter();

        return (reader, parsedLine, candidates) ->
        {
            int before = candidates.size();

            commandCompleter.complete(reader, parsedLine, candidates);

            if (candidates.size() == before || parsedLine.wordIndex() > 0)
            {
                if (parsedLine.wordIndex() == 0)
                {
                    pathCompleter.complete(reader, parsedLine, candidates);
                    fileCompleter.complete(reader, parsedLine, candidates);
                }
                else
                    fileCompleter.complete(reader, parsedLine, candidates);
            }
        };
    }

    public static void main(String[] args) throws IOException
    {
        try
        {
            StartupDirectory.ensureStartupDirectory();
        }
        catch (Exception e)
        {
            ErrorPrinter.print(
                    Ansi.withForeground("Warning", Ansi.Foreground.YELLOW)
                            + ": Could not set initial directory to home: " + e.getMessage()
            );
        }

        REPL repl = new REPL();

        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        TerminalShare.setSharedTerminal(terminal);

        Map<ReservedWords, Function<List<String>, CommandResult>> commands = REPL.getCommands();

        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(new char[0]);

        terminal.handle(Terminal.Signal.INT, _ -> FileSystemUtils.interruptCurrentProcess());

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(parser)
                .completer(createFallbackCompleter(commands))
                .option(LineReader.Option.INSERT_TAB, false)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .option(LineReader.Option.CASE_INSENSITIVE, true)
                .build();

        String eval = getString(args);

        if (eval != null)
        {
            if (!repl.executeCommand(eval))
            {
                PrinterUtils.printFormatted("", System.err);
                System.exit(1);
            }
            PrinterUtils.printFormatted("", System.out);
            System.exit(0);
        }

        repl.executeCommand("ver");

        while (true)
        {
            String prompt = getString(commands);
            String input;

            try
            {
                input = reader.readLine(prompt);
            }
            catch (UserInterruptException e)
            {
                System.out.printf("^C%nInterrupted.%n");
                continue;
            }
            catch (EndOfFileException e)
            {
                break;
            }

            if (input == null || input.isBlank())
                continue;

            reader.getHistory().add(input);

            if (repl.executeCommand(input))
                System.out.flush();
        }
    }

    private static String getString(String[] args)
    {
        String eval = null;

        for (int i = 0; i < args.length; i++)
        {
            String a = args[i];

            if (a.equals("-e"))
            {
                if (eval != null)
                    usage();
                if (i + 1 >= args.length)
                    usage();
                eval = args[++i];
                continue;
            }

            if (a.equalsIgnoreCase("--EXECUTE"))
            {
                if (eval != null)
                    usage();
                if (i + 1 >= args.length)
                    usage();
                eval = args[++i];
                continue;
            }

            if (a.startsWith("-e="))
            {
                if (eval != null)
                    usage();
                eval = a.substring(3);
                continue;
            }

            usage();
        }

        return eval;
    }

    private static String getString(Map<ReservedWords, Function<List<String>, CommandResult>> commands)
    {
        String home = FileSystemUtils.getHomeDirectory();
        String currentDir = CurrentDirectory.get().toString();

        String prompt;

        if (commands.containsKey(ReservedWords.WHEREAMI))
            prompt = currentDir + Ansi.withForeground("> ", Ansi.Foreground.YELLOW);
        else
            prompt = "> ";

        if (home != null && !home.isEmpty() && prompt.startsWith(home))
            prompt = Ansi.withForeground("~", Ansi.Foreground.GREEN) + prompt.substring(home.length());

        return prompt;
    }

    private static void usage()
    {
        PrinterUtils.printFormatted("usage: jcmdshell [(-e <command> | -e=<command> | --EXECUTE <command>)]", System.err);
        System.exit(2);
    }
}
