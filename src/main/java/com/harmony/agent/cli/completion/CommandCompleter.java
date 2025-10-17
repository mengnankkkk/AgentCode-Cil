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
import java.util.stream.Stream;

/**
 * Command completer for interactive mode
 * Provides tab completion for slash commands and file paths
 */
public class CommandCompleter implements Completer {

    private static final List<CommandInfo> COMMANDS = List.of(
        new CommandInfo("/analyze", "Analyze code for security issues", true),
        new CommandInfo("/suggest", "Get AI suggestions for fixes", true),
        new CommandInfo("/refactor", "Get refactoring recommendations", true),
        new CommandInfo("/config", "Show current configuration", false),
        new CommandInfo("/history", "Show conversation history", false),
        new CommandInfo("/clear", "Clear screen", false),
        new CommandInfo("/help", "Show help information", false),
        new CommandInfo("/exit", "Exit interactive mode", false),
        new CommandInfo("/quit", "Exit interactive mode (alias)", false),
        new CommandInfo("/q", "Exit interactive mode (short)", false)
    );

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        String word = line.word();

        // If buffer starts with /, complete commands
        if (buffer.startsWith("/")) {
            completeCommand(buffer, word, candidates);
        }
        // If buffer contains a command that needs file path
        else if (needsFilePathCompletion(buffer)) {
            completeFilePath(buffer, word, candidates);
        }
    }

    /**
     * Complete slash commands
     */
    private void completeCommand(String buffer, String word, List<Candidate> candidates) {
        // Find commands that match the current input
        String input = buffer.trim();

        for (CommandInfo cmd : COMMANDS) {
            if (cmd.name.startsWith(input)) {
                candidates.add(new Candidate(
                    cmd.name,
                    cmd.name,
                    null,
                    cmd.description,
                    null,
                    null,
                    true
                ));
            }
        }

        // If we have exactly one match and it needs a file path, add space
        if (candidates.size() == 1) {
            CommandInfo matched = findCommand(candidates.get(0).value());
            if (matched != null && matched.needsFilePath) {
                candidates.clear();
                candidates.add(new Candidate(
                    matched.name + " ",
                    matched.name + " ",
                    null,
                    matched.description,
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

        try {
            Path basePath;
            String prefix;

            if (pathInput.isEmpty() || pathInput.equals(".")) {
                basePath = Paths.get(".");
                prefix = "";
            } else if (pathInput.contains(File.separator)) {
                int lastSep = pathInput.lastIndexOf(File.separator);
                String dir = pathInput.substring(0, lastSep + 1);
                prefix = pathInput.substring(lastSep + 1);
                basePath = Paths.get(dir.isEmpty() ? "." : dir);
            } else {
                basePath = Paths.get(".");
                prefix = pathInput;
            }

            if (Files.exists(basePath) && Files.isDirectory(basePath)) {
                try (Stream<Path> paths = Files.list(basePath)) {
                    paths
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return prefix.isEmpty() || name.startsWith(prefix);
                        })
                        .forEach(p -> {
                            String name = p.getFileName().toString();
                            boolean isDir = Files.isDirectory(p);

                            // Build the completion value
                            String completion;
                            if (pathInput.contains(File.separator)) {
                                int lastSep = pathInput.lastIndexOf(File.separator);
                                String dir = pathInput.substring(0, lastSep + 1);
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
     * Check if current buffer needs file path completion
     */
    private boolean needsFilePathCompletion(String buffer) {
        String trimmed = buffer.trim();

        for (CommandInfo cmd : COMMANDS) {
            if (cmd.needsFilePath && trimmed.startsWith(cmd.name + " ")) {
                return true;
            }
        }

        return false;
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
