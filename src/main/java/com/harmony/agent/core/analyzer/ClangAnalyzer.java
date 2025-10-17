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

/**
 * Clang-Tidy static analyzer integration
 */
public class ClangAnalyzer implements Analyzer {

    private static final Logger logger = LoggerFactory.getLogger(ClangAnalyzer.class);

    private final String clangPath;

    public ClangAnalyzer() {
        ConfigManager configManager = ConfigManager.getInstance();
        this.clangPath = configManager.getConfig().getTools().getClangPath();
    }

    public ClangAnalyzer(String clangPath) {
        this.clangPath = clangPath;
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
        if (!Files.exists(file)) {
            throw new AnalyzerException("File not found: " + file);
        }

        if (!isAvailable()) {
            throw new AnalyzerException("Clang-Tidy is not available. Please install clang-tidy.");
        }

        logger.info("Analyzing file with Clang-Tidy: {}", file);

        try {
            // Run clang-tidy with security checks
            ProcessBuilder pb = new ProcessBuilder(
                clangPath,
                "-checks=-*,clang-analyzer-*,bugprone-*,cert-*,concurrency-*",
                "--export-fixes=-",
                "--format-style=json",
                file.toString()
            );

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
            logger.debug("Clang-Tidy exit code: {}", exitCode);

            return parseClangOutput(output.toString(), file);

        } catch (IOException | InterruptedException e) {
            throw new AnalyzerException("Failed to run Clang-Tidy", e);
        }
    }

    @Override
    public List<SecurityIssue> analyzeAll(List<Path> files) throws AnalyzerException {
        List<SecurityIssue> allIssues = new ArrayList<>();

        for (Path file : files) {
            try {
                List<SecurityIssue> issues = analyze(file);
                allIssues.addAll(issues);
            } catch (AnalyzerException e) {
                logger.error("Failed to analyze file: {}", file, e);
                // Continue with next file
            }
        }

        return allIssues;
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
