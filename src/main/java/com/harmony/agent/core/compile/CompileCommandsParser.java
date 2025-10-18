package com.harmony.agent.core.compile;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parser for compile_commands.json
 *
 * Format:
 * [
 *   {
 *     "directory": "/path/to/project/build",
 *     "command": "gcc -c -o main.o main.c -I../include",
 *     "file": "/path/to/project/main.c"
 *   }
 * ]
 */
public class CompileCommandsParser {

    private static final Logger logger = LoggerFactory.getLogger(CompileCommandsParser.class);

    private final Path compileCommandsPath;
    private final Gson gson;
    private List<CompileCommand> commands;

    public CompileCommandsParser(File projectRoot) {
        this.compileCommandsPath = projectRoot.toPath().resolve("compile_commands.json");
        this.gson = new Gson();
        this.commands = null;
    }

    /**
     * Load and parse compile_commands.json
     */
    public boolean load() {
        if (!Files.exists(compileCommandsPath)) {
            logger.warn("compile_commands.json not found at: {}", compileCommandsPath);
            return false;
        }

        try {
            String json = Files.readString(compileCommandsPath);
            commands = gson.fromJson(json, new TypeToken<List<CompileCommand>>(){}.getType());
            logger.info("Loaded {} compile commands", commands.size());
            return true;

        } catch (IOException e) {
            logger.error("Failed to read compile_commands.json", e);
            return false;
        } catch (Exception e) {
            logger.error("Failed to parse compile_commands.json", e);
            return false;
        }
    }

    /**
     * Get compile command for a specific file
     */
    public Optional<CompileCommand> getCommandForFile(String filePath) {
        if (commands == null) {
            return Optional.empty();
        }

        Path targetPath = Paths.get(filePath).toAbsolutePath().normalize();

        for (CompileCommand cmd : commands) {
            Path cmdFilePath = Paths.get(cmd.file).toAbsolutePath().normalize();
            if (cmdFilePath.equals(targetPath)) {
                return Optional.of(cmd);
            }
        }

        // Try matching by filename only
        String fileName = targetPath.getFileName().toString();
        for (CompileCommand cmd : commands) {
            if (cmd.file.endsWith(fileName)) {
                return Optional.of(cmd);
            }
        }

        return Optional.empty();
    }

    /**
     * Get all compile commands
     */
    public List<CompileCommand> getAllCommands() {
        return commands != null ? new ArrayList<>(commands) : new ArrayList<>();
    }

    /**
     * Compile command entry from compile_commands.json
     */
    public static class CompileCommand {
        public String directory;
        public String command;
        public String file;
        public List<String> arguments;  // Alternative to 'command' field

        /**
         * Get the compiler command as a list of arguments
         */
        public List<String> getCommandArgs() {
            if (arguments != null && !arguments.isEmpty()) {
                return arguments;
            }

            // Parse command string
            List<String> args = new ArrayList<>();
            if (command != null) {
                // Simple parsing - splits on spaces, handles quotes
                boolean inQuotes = false;
                StringBuilder current = new StringBuilder();

                for (char c : command.toCharArray()) {
                    if (c == '"' || c == '\'') {
                        inQuotes = !inQuotes;
                    } else if (c == ' ' && !inQuotes) {
                        if (current.length() > 0) {
                            args.add(current.toString());
                            current = new StringBuilder();
                        }
                    } else {
                        current.append(c);
                    }
                }

                if (current.length() > 0) {
                    args.add(current.toString());
                }
            }

            return args;
        }

        /**
         * Get the compiler executable (first argument)
         */
        public String getCompiler() {
            List<String> args = getCommandArgs();
            return args.isEmpty() ? "gcc" : args.get(0);
        }

        /**
         * Get compiler flags and arguments (excluding input/output files)
         */
        public List<String> getCompilerFlags() {
            List<String> args = getCommandArgs();
            List<String> flags = new ArrayList<>();

            for (int i = 1; i < args.size(); i++) {
                String arg = args.get(i);

                // Skip output file specification
                if (arg.equals("-o")) {
                    i++; // Skip next argument (output file)
                    continue;
                }

                // Skip input file (the source file being compiled)
                if (arg.equals(file) || arg.endsWith(new File(file).getName())) {
                    continue;
                }

                flags.add(arg);
            }

            return flags;
        }

        @Override
        public String toString() {
            return String.format("CompileCommand{file='%s', directory='%s', compiler='%s'}",
                file, directory, getCompiler());
        }
    }
}
