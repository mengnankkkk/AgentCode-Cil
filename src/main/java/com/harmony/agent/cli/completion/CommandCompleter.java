package com.harmony.agent.cli.completion;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Command completer for interactive mode
 * Provides tab completion for slash commands, system commands, and file paths
 */
public class CommandCompleter implements Completer {

    private final Supplier<File> workingDirectorySupplier;

    // Slash commands
    private static final List<CommandInfo> COMMANDS = List.of(
        // Task Planning
        new CommandInfo("/plan", "Break down requirement into tasks", false),
        new CommandInfo("/next", "Execute current task", false),
        new CommandInfo("/tasks", "Show all tasks", false),
        new CommandInfo("/current", "Show current task only", false),
        // Analysis & Tools
        new CommandInfo("/analyze", "Analyze code for security issues", true),
        new CommandInfo("/suggest", "Get AI suggestions for fixes", true),
        new CommandInfo("/refactor", "Get refactoring recommendations", true),
        // Build & Test Tools
        new CommandInfo("/compile", "Run Maven compilation", false),
        new CommandInfo("/test", "Run JUnit tests", false),
        new CommandInfo("/spotbugs", "Run SpotBugs static analysis", false),
        // General
        new CommandInfo("/config", "Show current configuration", false),
        new CommandInfo("/history", "Show conversation history", false),
        new CommandInfo("/clear", "Clear screen", false),
        new CommandInfo("/help", "Show help information", false),
        new CommandInfo("/exit", "Exit interactive mode", false),
        new CommandInfo("/quit", "Exit interactive mode (alias)", false),
        new CommandInfo("/q", "Exit interactive mode (short)", false)
    );

    // System commands (common cross-platform commands)
    private static final List<CommandInfo> SYSTEM_COMMANDS = List.of(
        // Directory operations
        new CommandInfo("$ pwd", "Print working directory", false),
        new CommandInfo("$ cd", "Change directory", true),
        new CommandInfo("$ ls", "List directory contents (Unix/Windows)", true),
        new CommandInfo("$ dir", "List directory contents (Windows)", true),
        // File operations
        new CommandInfo("$ cat", "Display file contents", true),
        new CommandInfo("$ more", "Display file contents (paginated)", true),
        new CommandInfo("$ less", "Display file contents (scrollable)", true),
        new CommandInfo("$ head", "Display first lines of file", true),
        new CommandInfo("$ tail", "Display last lines of file", true),
        // File management
        new CommandInfo("$ cp", "Copy files/directories", true),
        new CommandInfo("$ mv", "Move/rename files", true),
        new CommandInfo("$ mkdir", "Create directory", true),
        new CommandInfo("$ touch", "Create empty file", true),
        new CommandInfo("$ rm", "Remove file (use with caution)", true),
        // Search and info
        new CommandInfo("$ find", "Search for files", true),
        new CommandInfo("$ grep", "Search text in files", true),
        new CommandInfo("$ echo", "Print text/variables", false),
        new CommandInfo("$ which", "Locate command", false),
        // Development tools
        new CommandInfo("$ mvn", "Maven build tool", false),
        new CommandInfo("$ java", "Run Java program", true),
        new CommandInfo("$ javac", "Compile Java code", true),
        new CommandInfo("$ python", "Run Python script", true),
        new CommandInfo("$ python3", "Run Python 3 script", true),
        new CommandInfo("$ node", "Run Node.js script", true),
        new CommandInfo("$ npm", "Node package manager", false),
        new CommandInfo("$ git", "Git version control", false)
    );

    /**
     * Constructor with working directory supplier
     * @param workingDirectorySupplier Supplier that provides the current working directory
     */
    public CommandCompleter(Supplier<File> workingDirectorySupplier) {
        this.workingDirectorySupplier = workingDirectorySupplier;
    }

    /**
     * Get current working directory
     */
    private File getCurrentWorkingDirectory() {
        return workingDirectorySupplier.get();
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line().trim();  // Trim whitespace
        String word = line.word();

        // If buffer starts with /, complete slash commands or their paths
        if (buffer.startsWith("/")) {
            if (buffer.contains(" ")) {
                // Has space, complete file path
                completeFilePath(buffer, word, candidates);
            } else {
                // Just command, complete command names
                completeCommand(buffer, word, candidates);
            }
        }
        // If buffer starts with $, complete system commands or their paths
        else if (buffer.startsWith("$")) {
            if (buffer.contains(" ")) {
                // Has space, complete file path
                completeSystemFilePath(buffer, word, candidates);
            } else {
                // Just command, complete command names
                completeSystemCommand(buffer, word, candidates);
            }
        }
    }

    /**
     * Complete slash commands
     */
    private void completeCommand(String buffer, String word, List<Candidate> candidates) {
        // Extract the command part after /
        String input = buffer.substring(1).trim();  // Remove "/"

        for (CommandInfo cmd : COMMANDS) {
            String cmdName = cmd.name.substring(1);  // Remove "/" from stored name
            if (cmdName.startsWith(input)) {
                candidates.add(new Candidate(
                    cmd.name,           // value: "/plan", "/analyze" (complete with prefix)
                    cmdName,            // display: "plan", "analyze" (show without prefix)
                    null,
                    cmd.description,    // description shown in table
                    null,
                    null,
                    false
                ));
            }
        }
    }

    /**
     * Complete file paths for commands like /analyze
     */
    private void completeFilePath(String buffer, String word, List<Candidate> candidates) {
        // Extract the file path part
        String[] parts = buffer.trim().split("\\s+", 2);
        String pathInput = parts.length > 1 ? parts[1] : "";

        completePathInternal(pathInput, candidates, false);
    }

    /**
     * Find command info by name
     */
    private CommandInfo findCommand(String name) {
        for (CommandInfo cmd : COMMANDS) {
            if (cmd.name.equals(name)) {
                return cmd;
            }
        }
        return null;
    }

    /**
     * Find system command info by name
     */
    private CommandInfo findSystemCommand(String name) {
        for (CommandInfo cmd : SYSTEM_COMMANDS) {
            if (cmd.name.equals(name)) {
                return cmd;
            }
        }
        return null;
    }

    /**
     * Complete system commands ($ prefix)
     */
    private void completeSystemCommand(String buffer, String word, List<Candidate> candidates) {
        // Extract the command part after $
        String input = buffer.substring(1).trim();  // Remove "$"

        for (CommandInfo cmd : SYSTEM_COMMANDS) {
            String cmdName = cmd.name.substring(2);  // Remove "$ " from stored name
            if (cmdName.startsWith(input)) {
                // Return compact format: $cd, $cat (no space to avoid Windows escaping)
                String compactValue = "$" + cmdName;

                candidates.add(new Candidate(
                    compactValue,       // value: "$cd", "$cat" (compact, no space)
                    cmdName,            // display: "cd", "cat" (show without prefix)
                    null,
                    cmd.description,    // description shown in table
                    null,
                    null,
                    false
                ));
            }
        }
    }

    /**
     * Complete file paths for system commands
     */
    private void completeSystemFilePath(String buffer, String word, List<Candidate> candidates) {
        // Extract the file path part after the command
        String[] parts = buffer.trim().split("\\s+", 2);
        String pathInput = parts.length > 1 ? parts[1] : "";

        // For cd command, only show directories (match compact format: $cd)
        boolean directoriesOnly = buffer.trim().startsWith("$cd") || buffer.trim().startsWith("$ cd");

        completePathInternal(pathInput, candidates, directoriesOnly);
    }

    /**
     * Internal path completion logic (shared between slash and system commands)
     */
    private void completePathInternal(String pathInput, List<Candidate> candidates, boolean directoriesOnly) {
        try {
            Path basePath;
            String prefix;
            File currentWorkingDirectory = getCurrentWorkingDirectory();

            // Handle empty input or current directory
            if (pathInput.isEmpty() || pathInput.equals(".")) {
                basePath = currentWorkingDirectory.toPath();
                prefix = "";
            }
            // Handle paths with separators
            else if (pathInput.contains(File.separator) || pathInput.contains("/")) {
                // Normalize separator
                String normalizedPath = pathInput.replace("/", File.separator);

                int lastSep = normalizedPath.lastIndexOf(File.separator);
                String dir = normalizedPath.substring(0, lastSep + 1);
                prefix = normalizedPath.substring(lastSep + 1);

                // Handle absolute vs relative paths
                if (normalizedPath.startsWith(File.separator) || (File.separator.equals("\\") && normalizedPath.length() > 1 && normalizedPath.charAt(1) == ':')) {
                    basePath = Paths.get(dir.isEmpty() ? File.separator : dir);
                } else {
                    basePath = new File(currentWorkingDirectory, dir).toPath();
                }
            }
            // Simple filename in current directory
            else {
                basePath = currentWorkingDirectory.toPath();
                prefix = pathInput;
            }

            // Create final variables for lambda
            final String finalPrefix = prefix;
            final String finalPathInput = pathInput;

            // List matching files/directories
            if (Files.exists(basePath) && Files.isDirectory(basePath)) {
                try (Stream<Path> paths = Files.list(basePath)) {
                    paths
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            // For cd, only show directories
                            if (directoriesOnly && !Files.isDirectory(p)) {
                                return false;
                            }
                            return finalPrefix.isEmpty() || name.startsWith(finalPrefix);
                        })
                        .forEach(p -> {
                            String name = p.getFileName().toString();
                            boolean isDir = Files.isDirectory(p);

                            // Build the completion value
                            String completion;
                            if (finalPathInput.contains(File.separator) || finalPathInput.contains("/")) {
                                int lastSep = Math.max(
                                    finalPathInput.lastIndexOf(File.separator),
                                    finalPathInput.lastIndexOf("/")
                                );
                                String dir = finalPathInput.substring(0, lastSep + 1);
                                completion = dir + name;
                            } else {
                                completion = name;
                            }

                            // Add trailing slash for directories
                            if (isDir) {
                                completion += File.separator;
                            }

                            candidates.add(new Candidate(
                                completion,
                                name,
                                null,
                                isDir ? "directory" : "file",
                                null,
                                null,
                                !isDir  // complete if it's a file
                            ));
                        });
                }
            }
        } catch (Exception e) {
            // Silently ignore errors during completion
        }
    }

    /**
     * Command information for completion
     */
    private static class CommandInfo {
        final String name;
        final String description;
        final boolean needsFilePath;

        CommandInfo(String name, String description, boolean needsFilePath) {
            this.name = name;
            this.description = description;
            this.needsFilePath = needsFilePath;
        }
    }
}
