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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Semgrep static analyzer integration
 */
public class SemgrepAnalyzer implements Analyzer {

    private static final Logger logger = LoggerFactory.getLogger(SemgrepAnalyzer.class);

    private final String semgrepPath;
    private final Path rulesDir;

    public SemgrepAnalyzer() {
        ConfigManager configManager = ConfigManager.getInstance();
        this.semgrepPath = configManager.getConfig().getTools().getSemgrepPath();

        // Use rules from classpath or custom directory
        this.rulesDir = Paths.get("src/main/resources/rules");
    }

    public SemgrepAnalyzer(String semgrepPath, Path rulesDir) {
        this.semgrepPath = semgrepPath;
        this.rulesDir = rulesDir;
    }

    @Override
    public String getName() {
        return "Semgrep";
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(semgrepPath, "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.debug("Semgrep not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder(semgrepPath, "--version");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

                String line = reader.readLine();
                if (line != null) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get Semgrep version", e);
        }
        return "Unknown";
    }

    @Override
    public List<SecurityIssue> analyze(Path file) throws AnalyzerException {
        if (!Files.exists(file)) {
            throw new AnalyzerException("File not found: " + file);
        }

        if (!isAvailable()) {
            throw new AnalyzerException("Semgrep is not available. Please install semgrep: pip install semgrep");
        }

        logger.info("Analyzing file with Semgrep: {}", file);

        try {
            List<String> command = buildSemgrepCommand(file);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();

            // Read stdout
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading stdout", e);
                }
            });

            // Read stderr
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading stderr", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            int exitCode = process.waitFor();
            stdoutThread.join();
            stderrThread.join();

            logger.debug("Semgrep exit code: {}", exitCode);

            if (errorOutput.length() > 0) {
                logger.debug("Semgrep stderr: {}", errorOutput);
            }

            return parseSemgrepOutput(output.toString());

        } catch (IOException | InterruptedException e) {
            throw new AnalyzerException("Failed to run Semgrep", e);
        }
    }

    @Override
    public List<SecurityIssue> analyzeAll(List<Path> files) throws AnalyzerException {
        if (files.isEmpty()) {
            return new ArrayList<>();
        }

        if (!isAvailable()) {
            throw new AnalyzerException("Semgrep is not available. Please install semgrep: pip install semgrep");
        }

        // Semgrep can analyze multiple files efficiently in a single invocation
        logger.info("Analyzing {} files with Semgrep in batch mode", files.size());

        try {
            List<String> command = buildBatchSemgrepCommand(files);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();

            // Read stdout
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading stdout", e);
                }
            });

            // Read stderr
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading stderr", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            int exitCode = process.waitFor();
            stdoutThread.join();
            stderrThread.join();

            logger.debug("Semgrep batch exit code: {}", exitCode);

            if (errorOutput.length() > 0) {
                logger.debug("Semgrep stderr: {}", errorOutput);
            }

            return parseSemgrepOutput(output.toString());

        } catch (IOException | InterruptedException e) {
            throw new AnalyzerException("Failed to run Semgrep in batch mode", e);
        }
    }

    /**
     * Build Semgrep command with appropriate options
     */
    private List<String> buildSemgrepCommand(Path file) {
        List<String> command = new ArrayList<>();
        command.add(semgrepPath);
        command.add("--json");
        command.add("--quiet");
        command.add("--disable-version-check");

        // Use custom rules if available
        if (Files.exists(rulesDir)) {
            command.add("--config");
            command.add(rulesDir.toString());
        } else {
            // Use default security rules
            command.add("--config");
            command.add("auto");
        }

        command.add(file.toString());

        return command;
    }

    /**
     * Build Semgrep command for batch file analysis
     */
    private List<String> buildBatchSemgrepCommand(List<Path> files) {
        List<String> command = new ArrayList<>();
        command.add(semgrepPath);
        command.add("--json");
        command.add("--quiet");
        command.add("--disable-version-check");

        // Use custom rules if available
        if (Files.exists(rulesDir)) {
            command.add("--config");
            command.add(rulesDir.toString());
        } else {
            // Use default security rules
            command.add("--config");
            command.add("auto");
        }

        // Add all file paths
        for (Path file : files) {
            command.add(file.toString());
        }

        return command;
    }

    /**
     * Parse Semgrep JSON output
     */
    private List<SecurityIssue> parseSemgrepOutput(String jsonOutput) {
        List<SecurityIssue> issues = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();
            JsonArray results = root.getAsJsonArray("results");

            if (results == null) {
                logger.debug("No results found in Semgrep output");
                return issues;
            }

            for (JsonElement resultElement : results) {
                JsonObject result = resultElement.getAsJsonObject();
                SecurityIssue issue = parseSemgrepResult(result);
                if (issue != null) {
                    issues.add(issue);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to parse Semgrep JSON output", e);
        }

        logger.info("Found {} issues from Semgrep", issues.size());
        return issues;
    }

    /**
     * Parse a single Semgrep result
     */
    private SecurityIssue parseSemgrepResult(JsonObject result) {
        try {
            // Extract basic information
            String checkId = result.get("check_id").getAsString();
            String message = result.get("extra").getAsJsonObject()
                .get("message").getAsString();

            // Extract location
            JsonObject start = result.get("start").getAsJsonObject();
            String filePath = result.get("path").getAsString();
            int lineNumber = start.get("line").getAsInt();
            int columnNumber = start.get("col").getAsInt();

            // Extract code snippet
            JsonObject extra = result.get("extra").getAsJsonObject();
            String snippet = extra.has("lines") ?
                extra.get("lines").getAsString() : null;

            // Determine severity
            String severityStr = extra.has("severity") ?
                extra.get("severity").getAsString() : "INFO";
            IssueSeverity severity = mapSemgrepSeverity(severityStr);

            // Determine category from check_id
            IssueCategory category = determineCategory(checkId, message);

            // Create code location
            CodeLocation location = new CodeLocation(
                filePath,
                lineNumber,
                columnNumber,
                snippet
            );

            // Build issue
            SecurityIssue.Builder builder = new SecurityIssue.Builder()
                .id(UUID.randomUUID().toString())
                .title(checkId)
                .description(message)
                .severity(severity)
                .category(category)
                .location(location)
                .analyzer(getName())
                .metadata("check_id", checkId);

            // Add metadata
            if (extra.has("metadata")) {
                JsonObject metadata = extra.getAsJsonObject("metadata");
                for (String key : metadata.keySet()) {
                    builder.metadata("semgrep_" + key, metadata.get(key).getAsString());
                }
            }

            return builder.build();

        } catch (Exception e) {
            logger.warn("Failed to parse Semgrep result", e);
            return null;
        }
    }

    /**
     * Map Semgrep severity to our severity levels
     */
    private IssueSeverity mapSemgrepSeverity(String semgrepSeverity) {
        switch (semgrepSeverity.toUpperCase()) {
            case "ERROR":
                return IssueSeverity.CRITICAL;
            case "WARNING":
                return IssueSeverity.HIGH;
            case "INFO":
                return IssueSeverity.MEDIUM;
            default:
                return IssueSeverity.LOW;
        }
    }

    /**
     * Determine issue category from check ID and message
     */
    private IssueCategory determineCategory(String checkId, String message) {
        String lower = checkId.toLowerCase() + " " + message.toLowerCase();

        // Memory issues
        if (lower.contains("buffer-overflow") || lower.contains("buffer overflow"))
            return IssueCategory.BUFFER_OVERFLOW;
        if (lower.contains("use-after-free") || lower.contains("use after free"))
            return IssueCategory.USE_AFTER_FREE;
        if (lower.contains("memory-leak") || lower.contains("memory leak"))
            return IssueCategory.MEMORY_LEAK;
        if (lower.contains("null-deref") || lower.contains("null pointer"))
            return IssueCategory.NULL_DEREFERENCE;

        // Injection
        if (lower.contains("sql-injection") || lower.contains("sql injection"))
            return IssueCategory.SQL_INJECTION;
        if (lower.contains("command-injection") || lower.contains("command injection"))
            return IssueCategory.COMMAND_INJECTION;
        if (lower.contains("path-traversal") || lower.contains("path traversal"))
            return IssueCategory.PATH_TRAVERSAL;

        // Crypto
        if (lower.contains("weak-crypto") || lower.contains("weak hash"))
            return IssueCategory.WEAK_CRYPTO;
        if (lower.contains("hardcoded") && (lower.contains("key") || lower.contains("password")))
            return IssueCategory.HARDCODED_SECRET;
        if (lower.contains("insecure-random"))
            return IssueCategory.INSECURE_RANDOM;

        // Concurrency
        if (lower.contains("race-condition") || lower.contains("race condition"))
            return IssueCategory.RACE_CONDITION;
        if (lower.contains("deadlock"))
            return IssueCategory.DEADLOCK;

        // Resource management
        if (lower.contains("resource-leak") || lower.contains("file.*not.*closed"))
            return IssueCategory.RESOURCE_LEAK;

        return IssueCategory.UNKNOWN;
    }
}
