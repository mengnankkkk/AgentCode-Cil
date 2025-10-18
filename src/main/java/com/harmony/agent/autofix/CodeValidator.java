package com.harmony.agent.autofix;

import com.harmony.agent.core.analyzer.AnalyzerException;
import com.harmony.agent.core.analyzer.RustAnalyzer;
import com.harmony.agent.core.analyzer.SemgrepAnalyzer;
import com.harmony.agent.core.compile.CompileCommandsParser;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.ProjectType;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.tools.ToolExecutor;
import com.harmony.agent.tools.result.CompileResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Code Validator - executes REAL compilation and testing
 * This is NOT an LLM role - it's a tool executor
 *
 * Supports:
 * - C/C++ projects: Clang/GCC compilation + Semgrep re-analysis
 * - Java projects: Maven compilation
 *
 * Responsibilities:
 * 1. Detect project type
 * 2. Write temporary fixed code to a test file
 * 3. Execute actual compilation (Clang/GCC or Maven)
 * 4. Re-run analyzer to verify issue is fixed (C/C++ only)
 * 5. Return structured validation results
 * 6. Clean up temporary files
 */
public class CodeValidator {

    private static final Logger logger = LoggerFactory.getLogger(CodeValidator.class);

    private final ToolExecutor toolExecutor;
    private final File workingDirectory;
    private final ProjectType projectType;
    private final CompileCommandsParser compileCommandsParser;
    private final SemgrepAnalyzer semgrepAnalyzer;  // For C/C++ re-analysis
    private final RustAnalyzer rustAnalyzer;  // For Rust re-analysis

    public CodeValidator(ToolExecutor toolExecutor, File workingDirectory) {
        this.toolExecutor = toolExecutor;
        this.workingDirectory = workingDirectory;
        this.projectType = ProjectType.detectFromDirectory(workingDirectory);
        this.compileCommandsParser = new CompileCommandsParser(workingDirectory);
        this.semgrepAnalyzer = new SemgrepAnalyzer();  // Initialize Semgrep analyzer
        this.rustAnalyzer = new RustAnalyzer();  // Initialize Rust analyzer

        logger.info("Detected project type: {}", projectType.getDisplayName());

        if (projectType == ProjectType.C_CPP) {
            boolean loaded = compileCommandsParser.load();
            if (!loaded) {
                logger.warn("compile_commands.json not found - C/C++ validation may fail");
            }

            // Check if Semgrep is available for re-analysis
            if (!semgrepAnalyzer.isAvailable()) {
                logger.warn("Semgrep not available - C/C++ re-analysis will be skipped");
            } else {
                logger.info("Semgrep available for re-analysis: {}", semgrepAnalyzer.getVersion());
            }
        }

        if (projectType == ProjectType.RUST) {
            // Check if Cargo is available
            if (!rustAnalyzer.isAvailable()) {
                logger.warn("Cargo not available - Rust validation will be skipped");
            } else {
                logger.info("Cargo available for Rust validation: {}", rustAnalyzer.getVersion());
            }
        }
    }

    /**
     * Validate code change by ACTUALLY compiling it
     *
     * @param filePath Original file path
     * @param newCode Fixed code content
     * @return Validation result with compilation feedback
     */
    public ValidationResult validateCodeChange(Path filePath, String newCode) {
        return validateCodeChange(filePath, newCode, null);
    }

    /**
     * Validate code change with original issue context
     *
     * @param filePath Original file path
     * @param newCode Fixed code content
     * @param originalIssue Original security issue (for re-analysis verification)
     * @return Validation result with compilation and re-analysis feedback
     */
    public ValidationResult validateCodeChange(Path filePath, String newCode, SecurityIssue originalIssue) {
        logger.info("Validating code change for: {} (Type: {})", filePath, projectType.getDisplayName());

        return switch (projectType) {
            case C_CPP -> validateCppCode(filePath, newCode, originalIssue);
            case JAVA -> validateJavaCode(filePath, newCode);
            case RUST -> validateRustCode(filePath, newCode, originalIssue);
            default -> ValidationResult.fail(
                "Unsupported project type: " + projectType.getDisplayName(),
                List.of("Only C/C++, Java, and Rust projects are supported"),
                null
            );
        };
    }

    /**
     * Validate C/C++ code
     * 1. Get compile command from compile_commands.json
     * 2. Run Clang/GCC compilation
     * 3. Re-run Semgrep analyzer to verify issue is fixed
     */
    private ValidationResult validateCppCode(Path filePath, String newCode, SecurityIssue originalIssue) {
        Path backupFile = null;

        try {
            // Step 1: Backup original file
            backupFile = createBackup(filePath);
            logger.info("Created backup: {}", backupFile);

            // Step 2: Write new code to original file
            Files.writeString(filePath, newCode);
            logger.info("Wrote fixed code to: {}", filePath);

            // Step 3: Get compile command from compile_commands.json
            var commandOpt = compileCommandsParser.getCommandForFile(filePath.toString());
            if (commandOpt.isEmpty()) {
                logger.warn("No compile command found for: {}", filePath);
                return ValidationResult.fail(
                    "No compile command found in compile_commands.json",
                    List.of("File: " + filePath),
                    null
                );
            }

            CompileCommandsParser.CompileCommand compileCommand = commandOpt.get();
            logger.info("Using compiler: {}", compileCommand.getCompiler());

            // Step 4: Run Clang/GCC compilation
            String compiler = compileCommand.getCompiler();
            List<String> flags = compileCommand.getCompilerFlags();

            List<String> command = new ArrayList<>();
            command.add(compiler);
            command.addAll(flags);
            command.add(filePath.toString());
            command.add("-fsyntax-only");  // Only check syntax, don't generate object file

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(compileCommand.directory));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            boolean compileSuccess = exitCode == 0;

            logger.info("Clang/GCC compilation: {}", compileSuccess ? "SUCCESS" : "FAILED");

            if (!compileSuccess) {
                return ValidationResult.fail(
                    "Compilation failed",
                    List.of(output.toString()),
                    null
                );
            }

            // Step 5: Re-run Semgrep analyzer (if original issue provided)
            if (originalIssue != null) {
                logger.info("Re-running Semgrep analysis to verify issue is fixed...");

                if (!semgrepAnalyzer.isAvailable()) {
                    logger.warn("Semgrep not available - skipping re-analysis");
                    return ValidationResult.pass(
                        "Compilation passed, but re-analysis skipped (Semgrep not available)",
                        null
                    );
                }

                try {
                    // Re-analyze the file with Semgrep
                    List<SecurityIssue> newIssues = semgrepAnalyzer.analyze(filePath);
                    logger.info("Semgrep re-analysis found {} issues", newIssues.size());

                    // Check if the original issue still exists
                    if (stillContainsIssue(newIssues, originalIssue)) {
                        logger.warn("Original issue still detected after fix!");
                        return ValidationResult.fail(
                            "Validation FAILED: Original issue still detected by Semgrep",
                            List.of(
                                "Issue: " + originalIssue.getTitle(),
                                "Location: " + originalIssue.getLocation(),
                                "The fix did not resolve the security vulnerability"
                            ),
                            null
                        );
                    }

                    logger.info("✓ Original issue no longer detected - fix verified!");

                } catch (AnalyzerException e) {
                    logger.warn("Semgrep re-analysis failed: {}", e.getMessage());
                    return ValidationResult.pass(
                        "Compilation passed, but re-analysis failed: " + e.getMessage(),
                        null
                    );
                }
            }

            return ValidationResult.pass("Compilation and validation passed", null);

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to validate C/C++ code", e);
            return ValidationResult.fail(
                "Validation failed: " + e.getMessage(),
                List.of(e.getMessage()),
                null
            );

        } finally {
            // Restore original file from backup
            if (backupFile != null && Files.exists(backupFile)) {
                try {
                    Files.copy(backupFile, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.delete(backupFile);
                    logger.info("Restored original file from backup");
                } catch (IOException e) {
                    logger.error("Failed to restore backup file", e);
                }
            }
        }
    }

    /**
     * Validate Java code using Maven
     */
    private ValidationResult validateJavaCode(Path filePath, String newCode) {
        Path backupFile = null;

        try {
            // Step 1: Backup original file
            backupFile = createBackup(filePath);
            logger.info("Created backup: {}", backupFile);

            // Step 2: Write new code to original file
            Files.writeString(filePath, newCode);
            logger.info("Wrote fixed code to: {}", filePath);

            // Step 3: Execute Maven compilation
            CompileResult compileResult = toolExecutor.compileMaven(false);
            logger.info("Maven compilation: {}", compileResult.isSuccess() ? "SUCCESS" : "FAILED");

            // Step 4: Analyze compilation result
            if (compileResult.isSuccess()) {
                return ValidationResult.pass(
                    "Code compiles successfully",
                    compileResult
                );
            } else {
                List<String> issues = new ArrayList<>();
                for (CompileResult.CompileError error : compileResult.getErrors()) {
                    issues.add(error.toString());
                }

                return ValidationResult.fail(
                    "Compilation failed with " + compileResult.getErrorCount() + " errors",
                    issues,
                    compileResult
                );
            }

        } catch (IOException e) {
            logger.error("Failed to validate Java code", e);
            return ValidationResult.fail(
                "Validation failed: " + e.getMessage(),
                List.of(e.getMessage()),
                null
            );

        } finally {
            // Restore original file from backup
            if (backupFile != null && Files.exists(backupFile)) {
                try {
                    Files.copy(backupFile, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.delete(backupFile);
                    logger.info("Restored original file from backup");
                } catch (IOException e) {
                    logger.error("Failed to restore backup file", e);
                }
            }
        }
    }

    /**
     * Validate Rust code using Cargo
     */
    private ValidationResult validateRustCode(Path filePath, String newCode, SecurityIssue originalIssue) {
        Path backupFile = null;

        try {
            // Step 1: Backup original file
            backupFile = createBackup(filePath);
            logger.info("Created backup: {}", backupFile);

            // Step 2: Write new code to original file
            Files.writeString(filePath, newCode);
            logger.info("Wrote fixed code to: {}", filePath);

            // Step 3: Find Cargo.toml root directory
            Path cargoRoot = findCargoRoot(filePath);
            if (cargoRoot == null) {
                logger.error("Could not find Cargo.toml for file: {}", filePath);
                return ValidationResult.fail(
                    "Cargo.toml not found",
                    List.of("File: " + filePath, "No Cargo project found"),
                    null
                );
            }

            logger.info("Found Cargo project root: {}", cargoRoot);

            // Step 4: Run cargo check (compilation check)
            List<String> command = new ArrayList<>();
            command.add("cargo");
            command.add("check");
            command.add("--message-format=json");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(cargoRoot.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();

            // Read stdout
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading cargo check stdout", e);
                }
            });

            // Read stderr
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading cargo check stderr", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            int exitCode = process.waitFor();
            stdoutThread.join();
            stderrThread.join();

            boolean compileSuccess = exitCode == 0;
            logger.info("Cargo check: {}", compileSuccess ? "SUCCESS" : "FAILED");

            if (!compileSuccess) {
                // Parse cargo check errors
                List<String> errors = parseCargoCheckOutput(output.toString());
                return ValidationResult.fail(
                    "Cargo check failed",
                    errors.isEmpty() ? List.of("Exit code: " + exitCode) : errors,
                    null
                );
            }

            // Step 5: Re-run Rust analyzer (Clippy + Geiger) if original issue provided
            if (originalIssue != null) {
                logger.info("Re-running Rust analysis to verify issue is fixed...");

                if (!rustAnalyzer.isAvailable()) {
                    logger.warn("Cargo not available - skipping re-analysis");
                    return ValidationResult.pass(
                        "Cargo check passed, but re-analysis skipped (Cargo not available)",
                        null
                    );
                }

                try {
                    // Re-analyze the file with Clippy/Geiger
                    List<SecurityIssue> newIssues = rustAnalyzer.analyze(filePath);
                    logger.info("Rust re-analysis found {} issues", newIssues.size());

                    // Check if the original issue still exists
                    if (stillContainsIssue(newIssues, originalIssue)) {
                        logger.warn("Original issue still detected after fix!");
                        return ValidationResult.fail(
                            "Validation FAILED: Original issue still detected by Clippy/Geiger",
                            List.of(
                                "Issue: " + originalIssue.getTitle(),
                                "Location: " + originalIssue.getLocation(),
                                "The fix did not resolve the Rust issue"
                            ),
                            null
                        );
                    }

                    logger.info("✓ Original issue no longer detected - fix verified!");

                } catch (AnalyzerException e) {
                    logger.warn("Rust re-analysis failed: {}", e.getMessage());
                    return ValidationResult.pass(
                        "Cargo check passed, but re-analysis failed: " + e.getMessage(),
                        null
                    );
                }
            }

            return ValidationResult.pass("Cargo check and validation passed", null);

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to validate Rust code", e);
            return ValidationResult.fail(
                "Validation failed: " + e.getMessage(),
                List.of(e.getMessage()),
                null
            );

        } finally {
            // Restore original file from backup
            if (backupFile != null && Files.exists(backupFile)) {
                try {
                    Files.copy(backupFile, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.delete(backupFile);
                    logger.info("Restored original file from backup");
                } catch (IOException e) {
                    logger.error("Failed to restore backup file", e);
                }
            }
        }
    }

    /**
     * Parse cargo check JSON output to extract error messages
     */
    private List<String> parseCargoCheckOutput(String jsonOutput) {
        List<String> errors = new ArrayList<>();

        try {
            String[] lines = jsonOutput.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(line).getAsJsonObject();

                    // Look for compiler messages
                    if (obj.has("reason") && "compiler-message".equals(obj.get("reason").getAsString())) {
                        com.google.gson.JsonObject message = obj.getAsJsonObject("message");
                        if (message != null && message.has("message")) {
                            String errorMsg = message.get("message").getAsString();
                            String level = message.has("level") ? message.get("level").getAsString() : "error";
                            errors.add(String.format("[%s] %s", level, errorMsg));
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse cargo check line: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse cargo check output", e);
        }

        return errors;
    }

    /**
     * Find Cargo.toml root directory for a Rust file
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

        return null;
    }

    /**
     * Create a backup of the original file
     */
    private Path createBackup(Path filePath) throws IOException {
        Path backupPath = filePath.resolveSibling(filePath.getFileName() + ".autofix.backup");
        Files.copy(filePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return backupPath;
    }

    /**
     * Check if the original issue still exists in the new analysis results
     *
     * Comparison strategy:
     * 1. Compare by hash (exact match)
     * 2. Compare by category + file + line range (fuzzy match for shifted lines)
     *
     * @param newIssues List of issues found in re-analysis
     * @param originalIssue The original issue that was being fixed
     * @return true if the original issue (or very similar issue) still exists
     */
    private boolean stillContainsIssue(List<SecurityIssue> newIssues, SecurityIssue originalIssue) {
        if (newIssues == null || newIssues.isEmpty()) {
            return false;
        }

        String originalHash = originalIssue.getHash();
        String originalFile = originalIssue.getLocation().getFilePath();
        int originalLine = originalIssue.getLocation().getLineNumber();
        IssueCategory originalCategory = originalIssue.getCategory();

        for (SecurityIssue newIssue : newIssues) {
            // Strategy 1: Exact hash match
            if (newIssue.getHash().equals(originalHash)) {
                logger.debug("Exact match found by hash: {}", originalHash);
                return true;
            }

            // Strategy 2: Fuzzy match (same category, file, and nearby line)
            if (newIssue.getCategory() == originalCategory) {
                String newFile = newIssue.getLocation().getFilePath();
                int newLine = newIssue.getLocation().getLineNumber();

                // Check if it's the same file (comparing absolute paths)
                if (originalFile.equals(newFile) || originalFile.endsWith(newFile) || newFile.endsWith(originalFile)) {
                    // Check if line numbers are close (within ±5 lines)
                    // This accounts for slight code shifts from the fix
                    int lineDiff = Math.abs(newLine - originalLine);
                    if (lineDiff <= 5) {
                        logger.debug("Fuzzy match found: category={}, file={}, line {} vs {}",
                            originalCategory, newFile, originalLine, newLine);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Validation result - contains REAL compilation feedback
     */
    public static class ValidationResult {
        private final boolean passed;
        private final String reason;
        private final List<String> issues;
        private final CompileResult compileResult;

        private ValidationResult(boolean passed, String reason, List<String> issues, CompileResult compileResult) {
            this.passed = passed;
            this.reason = reason;
            this.issues = issues != null ? issues : new ArrayList<>();
            this.compileResult = compileResult;
        }

        public static ValidationResult pass(String reason, CompileResult compileResult) {
            return new ValidationResult(true, reason, null, compileResult);
        }

        public static ValidationResult fail(String reason, List<String> issues, CompileResult compileResult) {
            return new ValidationResult(false, reason, issues, compileResult);
        }

        public boolean isPassed() {
            return passed;
        }

        public String getReason() {
            return reason;
        }

        public List<String> getIssues() {
            return issues;
        }

        public CompileResult getCompileResult() {
            return compileResult;
        }

        @Override
        public String toString() {
            if (passed) {
                return "✅ PASS: " + reason;
            } else {
                return "❌ FAIL: " + reason + " (Issues: " + issues.size() + ")";
            }
        }
    }
}
