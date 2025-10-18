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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rust static analyzer integration
 * Uses cargo clippy for linting and cargo geiger for unsafe code scanning
 */
public class RustAnalyzer implements Analyzer {

    private static final Logger logger = LoggerFactory.getLogger(RustAnalyzer.class);

    private final String cargoPath;
    private final Path projectRoot;

    public RustAnalyzer() {
        ConfigManager configManager = ConfigManager.getInstance();
        this.cargoPath = configManager.getConfig().getTools().getCargoPath();
        this.projectRoot = null;  // Will be determined from file path
    }

    public RustAnalyzer(String cargoPath, Path projectRoot) {
        this.cargoPath = cargoPath;
        this.projectRoot = projectRoot;
    }

    @Override
    public String getName() {
        return "Rust Analyzer (Clippy + Geiger)";
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(cargoPath, "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.debug("Cargo not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder(cargoPath, "--version");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

                String line = reader.readLine();
                if (line != null) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get Cargo version", e);
        }
        return "Unknown";
    }

    @Override
    public List<SecurityIssue> analyze(Path file) throws AnalyzerException {
        if (!Files.exists(file)) {
            throw new AnalyzerException("File not found: " + file);
        }

        if (!isAvailable()) {
            throw new AnalyzerException("Cargo is not available. Please install Rust: https://rustup.rs/");
        }

        // Find Cargo.toml root
        Path cargoRoot = findCargoRoot(file);
        if (cargoRoot == null) {
            throw new AnalyzerException("Could not find Cargo.toml for file: " + file);
        }

        logger.info("Analyzing Rust project at: {}", cargoRoot);

        List<SecurityIssue> issues = new ArrayList<>();

        // Run cargo clippy
        try {
            List<SecurityIssue> clippyIssues = runClippy(cargoRoot, file);
            issues.addAll(clippyIssues);
            logger.info("Clippy found {} issues", clippyIssues.size());
        } catch (Exception e) {
            logger.warn("Clippy analysis failed: {}", e.getMessage());
        }

        // Run cargo geiger (unsafe scanner)
        try {
            List<SecurityIssue> geigerIssues = runGeiger(cargoRoot, file);
            issues.addAll(geigerIssues);
            logger.info("Geiger found {} unsafe usage issues", geigerIssues.size());
        } catch (Exception e) {
            logger.warn("Geiger analysis failed: {}", e.getMessage());
        }

        return issues;
    }

    @Override
    public List<SecurityIssue> analyzeAll(List<Path> files) throws AnalyzerException {
        if (files.isEmpty()) {
            return new ArrayList<>();
        }

        if (!isAvailable()) {
            throw new AnalyzerException("Cargo is not available. Please install Rust: https://rustup.rs/");
        }

        // For Rust, we analyze the entire project at once (Cargo operates on the whole crate)
        Path firstFile = files.get(0);
        Path cargoRoot = findCargoRoot(firstFile);
        if (cargoRoot == null) {
            throw new AnalyzerException("Could not find Cargo.toml for files");
        }

        logger.info("Analyzing Rust project in batch mode at: {}", cargoRoot);

        List<SecurityIssue> issues = new ArrayList<>();

        // Run cargo clippy for all files
        try {
            List<SecurityIssue> clippyIssues = runClippy(cargoRoot, null);
            issues.addAll(clippyIssues);
        } catch (Exception e) {
            logger.warn("Clippy batch analysis failed: {}", e.getMessage());
        }

        // Run cargo geiger for all files
        try {
            List<SecurityIssue> geigerIssues = runGeiger(cargoRoot, null);
            issues.addAll(geigerIssues);
        } catch (Exception e) {
            logger.warn("Geiger batch analysis failed: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * Run cargo clippy and parse output
     */
    private List<SecurityIssue> runClippy(Path cargoRoot, Path specificFile) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(cargoPath);
        command.add("clippy");
        command.add("--message-format=json");
        command.add("--");
        command.add("-W");
        command.add("clippy::all");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cargoRoot.toFile());
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
                logger.error("Error reading Clippy stdout", e);
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
                logger.error("Error reading Clippy stderr", e);
            }
        });

        stdoutThread.start();
        stderrThread.start();

        int exitCode = process.waitFor();
        stdoutThread.join();
        stderrThread.join();

        logger.debug("Clippy exit code: {}", exitCode);

        return parseClippyOutput(output.toString(), specificFile);
    }

    /**
     * Run cargo geiger and parse output
     */
    private List<SecurityIssue> runGeiger(Path cargoRoot, Path specificFile) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(cargoPath);
        command.add("geiger");
        command.add("--output-format=Json");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cargoRoot.toFile());
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
                logger.error("Error reading Geiger stdout", e);
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
                logger.error("Error reading Geiger stderr", e);
            }
        });

        stdoutThread.start();
        stderrThread.start();

        int exitCode = process.waitFor();
        stdoutThread.join();
        stderrThread.join();

        logger.debug("Geiger exit code: {}", exitCode);

        if (exitCode != 0) {
            logger.warn("Geiger returned non-zero exit code. Stderr: {}", errorOutput);
        }

        return parseGeigerOutput(output.toString(), specificFile);
    }

    /**
     * Parse cargo clippy JSON output
     * Format: One JSON object per line, with "reason" field
     */
    private List<SecurityIssue> parseClippyOutput(String jsonOutput, Path specificFile) {
        List<SecurityIssue> issues = new ArrayList<>();

        try {
            String[] lines = jsonOutput.split("\n");

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    JsonObject obj = JsonParser.parseString(line).getAsJsonObject();

                    // Only process compiler messages
                    if (!obj.has("reason") || !"compiler-message".equals(obj.get("reason").getAsString())) {
                        continue;
                    }

                    JsonObject message = obj.getAsJsonObject("message");
                    if (message == null) continue;

                    // Parse the diagnostic message
                    SecurityIssue issue = parseClippyMessage(message);
                    if (issue != null) {
                        // Filter by specific file if provided
                        if (specificFile == null || issue.getLocation().getFilePath().equals(specificFile.toString())) {
                            issues.add(issue);
                        }
                    }

                } catch (Exception e) {
                    logger.debug("Failed to parse Clippy line: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Failed to parse Clippy JSON output", e);
        }

        logger.info("Parsed {} Clippy issues", issues.size());
        return issues;
    }

    /**
     * Parse a single Clippy diagnostic message
     */
    private SecurityIssue parseClippyMessage(JsonObject message) {
        try {
            String messageText = message.get("message").getAsString();
            String level = message.has("level") ? message.get("level").getAsString() : "warning";

            // Get code (like "clippy::unwrap_used")
            String code = null;
            if (message.has("code") && !message.get("code").isJsonNull()) {
                JsonObject codeObj = message.getAsJsonObject("code");
                if (codeObj.has("code")) {
                    code = codeObj.get("code").getAsString();
                }
            }

            // Get primary span (location)
            if (!message.has("spans") || message.get("spans").getAsJsonArray().size() == 0) {
                return null;  // No location info
            }

            JsonObject span = message.getAsJsonArray("spans").get(0).getAsJsonObject();
            String filePath = span.get("file_name").getAsString();
            int lineStart = span.get("line_start").getAsInt();
            int columnStart = span.get("column_start").getAsInt();

            // Get code snippet
            String snippet = span.has("text") && span.getAsJsonArray("text").size() > 0
                ? span.getAsJsonArray("text").get(0).getAsJsonObject().get("text").getAsString()
                : null;

            // Map severity
            IssueSeverity severity = mapClippySeverity(level, code);

            // Determine category
            IssueCategory category = determineClippyCategory(code, messageText);

            // Create location
            CodeLocation location = new CodeLocation(filePath, lineStart, columnStart, snippet);

            // Build issue
            String title = code != null ? code : "Clippy: " + level;

            return new SecurityIssue.Builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .description(messageText)
                .severity(severity)
                .category(category)
                .location(location)
                .analyzer(getName())
                .metadata("clippy_code", code != null ? code : "unknown")
                .metadata("clippy_level", level)
                .build();

        } catch (Exception e) {
            logger.warn("Failed to parse Clippy message", e);
            return null;
        }
    }

    /**
     * Parse cargo geiger JSON output
     * Geiger reports unsafe code usage
     */
    private List<SecurityIssue> parseGeigerOutput(String jsonOutput, Path specificFile) {
        List<SecurityIssue> issues = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();

            // Geiger output has "packages" array
            if (!root.has("packages")) {
                logger.debug("No packages found in Geiger output");
                return issues;
            }

            JsonArray packages = root.getAsJsonArray("packages");

            for (JsonElement pkgElement : packages) {
                JsonObject pkg = pkgElement.getAsJsonObject();

                // Get unsafe usage statistics
                if (!pkg.has("unsafety")) continue;

                JsonObject unsafety = pkg.getAsJsonObject("unsafety");

                // Check if there's unsafe code
                int unsafeUsed = unsafety.has("used") && unsafety.getAsJsonObject("used").has("unsafe")
                    ? unsafety.getAsJsonObject("used").get("unsafe").getAsInt()
                    : 0;

                if (unsafeUsed > 0) {
                    // Create an issue for unsafe usage
                    String packageName = pkg.has("name") ? pkg.get("name").getAsString() : "unknown";

                    // Try to get file location from package
                    String filePath = "unknown";
                    if (pkg.has("manifest_path")) {
                        filePath = pkg.get("manifest_path").getAsString();
                    }

                    CodeLocation location = new CodeLocation(filePath, 1, 1, null);

                    SecurityIssue issue = new SecurityIssue.Builder()
                        .id(UUID.randomUUID().toString())
                        .title("Unsafe code detected")
                        .description(String.format("Package '%s' contains %d unsafe usage(s)", packageName, unsafeUsed))
                        .severity(IssueSeverity.HIGH)  // Unsafe code is high priority
                        .category(IssueCategory.UNSAFE_CODE)
                        .location(location)
                        .analyzer(getName())
                        .metadata("geiger_unsafe_count", unsafeUsed)
                        .metadata("package", packageName)
                        .build();

                    issues.add(issue);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to parse Geiger JSON output", e);
        }

        logger.info("Parsed {} Geiger unsafe issues", issues.size());
        return issues;
    }

    /**
     * Map Clippy severity to our severity levels
     */
    private IssueSeverity mapClippySeverity(String level, String code) {
        // Some critical Clippy lints
        if (code != null) {
            if (code.contains("unwrap") || code.contains("expect") || code.contains("panic")) {
                return IssueSeverity.HIGH;
            }
            if (code.contains("unsafe")) {
                return IssueSeverity.CRITICAL;
            }
        }

        switch (level.toLowerCase()) {
            case "error":
                return IssueSeverity.CRITICAL;
            case "warning":
                return IssueSeverity.MEDIUM;
            case "note":
            case "help":
                return IssueSeverity.LOW;
            default:
                return IssueSeverity.INFO;
        }
    }

    /**
     * Determine issue category from Clippy code
     */
    private IssueCategory determineClippyCategory(String code, String message) {
        if (code == null) {
            return IssueCategory.UNKNOWN;
        }

        String lower = code.toLowerCase() + " " + message.toLowerCase();

        // Memory safety
        if (lower.contains("unsafe") || lower.contains("transmute")) {
            return IssueCategory.UNSAFE_CODE;
        }
        if (lower.contains("mem::forget") || lower.contains("leak")) {
            return IssueCategory.MEMORY_LEAK;
        }

        // Panic/unwrap issues
        if (lower.contains("unwrap") || lower.contains("expect") || lower.contains("panic")) {
            return IssueCategory.ERROR_HANDLING;
        }

        // Crypto
        if (lower.contains("weak") && lower.contains("crypto")) {
            return IssueCategory.WEAK_CRYPTO;
        }
        if (lower.contains("insecure") && lower.contains("random")) {
            return IssueCategory.INSECURE_RANDOM;
        }

        // Concurrency
        if (lower.contains("mutex") || lower.contains("deadlock") || lower.contains("race")) {
            return IssueCategory.RACE_CONDITION;
        }

        return IssueCategory.CODE_QUALITY;
    }

    /**
     * Find Cargo.toml root directory
     */
    private Path findCargoRoot(Path file) {
        Path current = file.getParent();

        while (current != null) {
            Path cargoToml = current.resolve("Cargo.toml");
            if (Files.exists(cargoToml)) {
                return current;
            }
            current = current.getParent();
        }

        return projectRoot;  // Fall back to provided project root
    }
}
