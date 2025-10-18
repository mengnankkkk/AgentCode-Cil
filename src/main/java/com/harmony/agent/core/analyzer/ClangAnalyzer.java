package com.harmony.agent.core.analyzer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.model.CodeLocation;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.SecurityIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Clang-Tidy static analyzer integration
 */
public class ClangAnalyzer implements Analyzer {

    private static final Logger logger = LoggerFactory.getLogger(ClangAnalyzer.class);

    private final String clangPath;
    private final String compileCommandsPath;
    private final ExecutorService executorService;

    public ClangAnalyzer() {
        ConfigManager configManager = ConfigManager.getInstance();
        this.clangPath = configManager.getConfig().getTools().getClangPath();
        this.compileCommandsPath = null;
        this.executorService = null; // Will use sequential execution
    }

    public ClangAnalyzer(String clangPath) {
        this(clangPath, null, null);
    }

    public ClangAnalyzer(String clangPath, String compileCommandsPath) {
        this(clangPath, compileCommandsPath, null);
    }

    /**
     * Constructor with ExecutorService for parallel file analysis
     *
     * @param clangPath Path to clang-tidy executable
     * @param compileCommandsPath Path to compile_commands.json (optional)
     * @param executorService ExecutorService for parallel execution (optional, uses sequential if null)
     */
    public ClangAnalyzer(String clangPath, String compileCommandsPath, ExecutorService executorService) {
        this.clangPath = clangPath;
        this.compileCommandsPath = compileCommandsPath;
        this.executorService = executorService;
    }

    @Override
    public String getName() {
        return "Clang-Tidy";
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(clangPath, "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.debug("Clang-Tidy not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder(clangPath, "--version");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

                String line = reader.readLine();
                if (line != null) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get Clang version", e);
        }
        return "Unknown";
    }

    @Override
    public List<SecurityIssue> analyze(Path file) throws AnalyzerException {
        return analyzeSingleFile(file);
    }

    @Override
    public List<SecurityIssue> analyzeAll(List<Path> files) throws AnalyzerException {
        if (files.isEmpty()) {
            return new ArrayList<>();
        }

        if (!isAvailable()) {
            throw new AnalyzerException("Clang-Tidy is not available. Please install clang-tidy.");
        }

        // Use parallel execution if ExecutorService is provided
        if (executorService != null) {
            return analyzeParallel(files);
        } else {
            return analyzeSequential(files);
        }
    }

    /**
     * Analyze files in parallel using ExecutorService
     */
    private List<SecurityIssue> analyzeParallel(List<Path> files) throws AnalyzerException {
        logger.info("Analyzing {} files with Clang-Tidy in parallel", files.size());

        // Create analysis tasks for each file
        List<Callable<List<SecurityIssue>>> tasks = new ArrayList<>();
        for (Path file : files) {
            tasks.add(() -> analyzeSingleFile(file));
        }

        // Thread-safe list to collect results
        List<SecurityIssue> allIssues = new CopyOnWriteArrayList<>();

        try {
            // Submit all tasks and wait for completion
            List<Future<List<SecurityIssue>>> futures = executorService.invokeAll(tasks);

            // Collect results from all futures
            for (Future<List<SecurityIssue>> future : futures) {
                try {
                    allIssues.addAll(future.get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof AnalyzerException) {
                        logger.error("Failed to analyze a file", cause);
                    } else {
                        logger.error("Unexpected error during file analysis", cause);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnalyzerException("Clang analysis was interrupted", e);
        }

        logger.info("Parallel Clang analysis complete, found {} issues", allIssues.size());
        return new ArrayList<>(allIssues);
    }

    /**
     * Analyze files sequentially (fallback when no ExecutorService)
     */
    private List<SecurityIssue> analyzeSequential(List<Path> files) {
        logger.info("Analyzing {} files with Clang-Tidy sequentially", files.size());

        List<SecurityIssue> allIssues = new ArrayList<>();
        for (Path file : files) {
            try {
                List<SecurityIssue> issues = analyzeSingleFile(file);
                allIssues.addAll(issues);
            } catch (AnalyzerException e) {
                logger.error("Failed to analyze file: {}", file, e);
                // Continue with next file
            }
        }

        logger.info("Sequential Clang analysis complete, found {} issues", allIssues.size());
        return allIssues;
    }

    /**
     * Analyze a single file with Clang-Tidy
     * This is the core analysis logic extracted for parallel execution
     */
    private List<SecurityIssue> analyzeSingleFile(Path file) throws AnalyzerException {
        if (!Files.exists(file)) {
            throw new AnalyzerException("File not found: " + file);
        }

        logger.debug("Analyzing file with Clang-Tidy: {}", file);

        try {
            // Build clang-tidy command
            List<String> command = buildClangCommand(file);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            logger.debug("Clang-Tidy exit code for {}: {}", file.getFileName(), exitCode);

            return parseClangOutput(output.toString(), file);

        } catch (IOException | InterruptedException e) {
            throw new AnalyzerException("Failed to run Clang-Tidy on " + file, e);
        }
    }

    /**
     * Build Clang-Tidy command with appropriate options
     */
    private List<String> buildClangCommand(Path file) {
        List<String> command = new ArrayList<>();
        command.add(clangPath);

        // Security-focused checks
        command.add("-checks=-*,clang-analyzer-*,bugprone-*,cert-*,concurrency-*");

        // If compile_commands.json is available, use it
        if (compileCommandsPath != null) {
            logger.debug("Using compile_commands.json from: {}", compileCommandsPath);
            // Get the directory containing compile_commands.json
            Path compileCommandsDir = Path.of(compileCommandsPath).getParent();
            if (compileCommandsDir != null) {
                command.add("-p");
                command.add(compileCommandsDir.toString());
            }
        } else {
            // Without compile_commands.json, add basic flags
            command.add("--");
            command.add("-std=c11");
        }

        command.add(file.toString());

        return command;
    }

    /**
     * Parse Clang-Tidy output
     */
    private List<SecurityIssue> parseClangOutput(String output, Path file) {
        List<SecurityIssue> issues = new ArrayList<>();

        try {
            // Clang-Tidy output format: file:line:column: warning: message [check-name]
            String[] lines = output.split("\n");

            for (String line : lines) {
                line = line.trim();

                // Skip empty lines and non-warning lines
                if (line.isEmpty() || !line.contains("warning:")) {
                    continue;
                }

                SecurityIssue issue = parseClangLine(line, file);
                if (issue != null) {
                    issues.add(issue);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to parse Clang output", e);
        }

        logger.info("Found {} issues in {}", issues.size(), file);
        return issues;
    }

    /**
     * Parse a single Clang-Tidy warning line
     */
    private SecurityIssue parseClangLine(String line, Path file) {
        try {
            // Example: /path/file.c:42:10: warning: use of undeclared identifier 'foo' [clang-diagnostic-undeclared-var-use]

            // Extract location
            String[] parts = line.split(":");
            if (parts.length < 4) {
                return null;
            }

            int lineNumber = Integer.parseInt(parts[1].trim());
            int columnNumber = Integer.parseInt(parts[2].trim());

            // Extract message and check name
            String messagePart = line.substring(line.indexOf("warning:") + 8);
            String message = messagePart;
            String checkName = "";

            int bracketStart = messagePart.indexOf('[');
            int bracketEnd = messagePart.indexOf(']');

            if (bracketStart >= 0 && bracketEnd > bracketStart) {
                message = messagePart.substring(0, bracketStart).trim();
                checkName = messagePart.substring(bracketStart + 1, bracketEnd);
            }

            // Determine severity and category
            IssueSeverity severity = determineSeverity(checkName, message);
            IssueCategory category = determineCategory(checkName, message);

            // Create code location
            CodeLocation location = new CodeLocation(
                file.toString(),
                lineNumber,
                columnNumber,
                null
            );

            // Build issue
            return new SecurityIssue.Builder()
                .id(UUID.randomUUID().toString())
                .title(message)
                .description(String.format("Clang-Tidy check: %s", checkName))
                .severity(severity)
                .category(category)
                .location(location)
                .analyzer(getName())
                .metadata("check_name", checkName)
                .build();

        } catch (Exception e) {
            logger.warn("Failed to parse Clang line: {}", line, e);
            return null;
        }
    }

    /**
     * Determine issue severity based on check name
     */
    private IssueSeverity determineSeverity(String checkName, String message) {
        String lower = checkName.toLowerCase();

        // Critical issues
        if (lower.contains("buffer") || lower.contains("overflow") ||
            lower.contains("use-after-free") || lower.contains("double-free")) {
            return IssueSeverity.CRITICAL;
        }

        // High severity issues
        if (lower.contains("security") || lower.contains("cert") ||
            lower.contains("concurrency") || lower.contains("deadlock")) {
            return IssueSeverity.HIGH;
        }

        // Medium severity issues
        if (lower.contains("bugprone") || lower.contains("leak")) {
            return IssueSeverity.MEDIUM;
        }

        // Default to low
        return IssueSeverity.LOW;
    }

    /**
     * Determine issue category based on check name
     */
    private IssueCategory determineCategory(String checkName, String message) {
        String lower = checkName.toLowerCase();

        // Memory issues
        if (lower.contains("buffer")) return IssueCategory.BUFFER_OVERFLOW;
        if (lower.contains("use-after-free")) return IssueCategory.USE_AFTER_FREE;
        if (lower.contains("leak")) return IssueCategory.MEMORY_LEAK;
        if (lower.contains("null")) return IssueCategory.NULL_DEREFERENCE;
        if (lower.contains("double-free")) return IssueCategory.DOUBLE_FREE;

        // Concurrency issues
        if (lower.contains("race")) return IssueCategory.RACE_CONDITION;
        if (lower.contains("deadlock")) return IssueCategory.DEADLOCK;
        if (lower.contains("thread")) return IssueCategory.THREAD_SAFETY;

        // Default
        return IssueCategory.UNKNOWN;
    }
}
