package com.harmony.agent;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Tests
 * Tests complete user workflows from command execution to result validation
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class E2ETest {

    private static final Logger logger = LoggerFactory.getLogger(E2ETest.class);

    private static Path testResourcesDir;
    private static Path testOutputDir;
    private ByteArrayOutputStream outputCapture;
    private PrintStream originalOut;

    @BeforeAll
    static void setupTestEnvironment() throws IOException {
        logger.info("Setting up E2E test environment");

        // Setup test directories
        testResourcesDir = Paths.get("src/test/resources/e2e");
        testOutputDir = Paths.get("target/test-output/e2e");

        // Create output directory
        if (!Files.exists(testOutputDir)) {
            Files.createDirectories(testOutputDir);
        }

        logger.info("Test resources directory: {}", testResourcesDir.toAbsolutePath());
        logger.info("Test output directory: {}", testOutputDir.toAbsolutePath());
    }

    @BeforeEach
    void captureOutput() {
        // Capture System.out for testing
        outputCapture = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputCapture));
    }

    @AfterEach
    void restoreOutput() {
        // Restore System.out
        System.setOut(originalOut);
    }

    @AfterAll
    static void cleanup() {
        logger.info("E2E test environment cleanup complete");
    }

    /**
     * E2E Test 1: Complete analyze workflow
     * Tests: analyze command → report generation → AI enhancement
     */
    @Test
    @Order(1)
    @DisplayName("E2E: Analyze command with HTML report generation")
    void testAnalyzeCommand_E2E() throws Exception {
        logger.info("Starting E2E test: Analyze command");

        // Setup
        Path bzip2Project = testResourcesDir.resolve("bzip2");
        Path compileCommandsPath = bzip2Project.resolve("compile_commands.json");
        Path reportOutputPath = testOutputDir.resolve("bzip2-analysis-report.html");

        // Verify test project exists
        assertTrue(Files.exists(bzip2Project), "bzip2 test project should exist");
        assertTrue(Files.exists(compileCommandsPath), "compile_commands.json should exist");

        logger.info("Test project: {}", bzip2Project.toAbsolutePath());
        logger.info("Report output: {}", reportOutputPath.toAbsolutePath());

        // Execute analyze command
        long startTime = System.currentTimeMillis();

        String output = executeCommand(
            "analyze",
            bzip2Project.toString(),
            "--level", "standard",
            "--compile-commands", compileCommandsPath.toString(),
            "-o", reportOutputPath.toString()
        );

        long duration = System.currentTimeMillis() - startTime;

        // Verify command execution
        assertNotNull(output, "Command output should not be null");
        logger.info("Command completed in {} ms", duration);
        logger.info("Command output preview: {}",
            output.substring(0, Math.min(500, output.length())));

        // Verify HTML report is generated
        assertFileExistsAndNotEmpty(reportOutputPath);
        logger.info("HTML report generated successfully");

        // Validate report structure and content
        String htmlContent = Files.readString(reportOutputPath);

        // Check basic HTML structure
        assertTrue(htmlContent.contains("<html"), "Should contain HTML tag");
        assertTrue(htmlContent.contains("</html>"), "Should have closing HTML tag");
        assertTrue(htmlContent.contains("HarmonySafe"), "Should contain project title");

        // Check report sections
        assertTrue(htmlContent.contains("安全扫描报告") || htmlContent.contains("Security Analysis Report"),
            "Should contain report header");
        assertTrue(htmlContent.contains("问题列表") || htmlContent.contains("Issues"),
            "Should contain issues section");

        // Check AI enhancement indicators (if AI is enabled)
        // Note: These checks are conditional - they may not appear if --no-ai was used
        boolean hasAiContent = htmlContent.contains("AI 专家分析")
            || htmlContent.contains("AI Expert Analysis")
            || htmlContent.contains("🤖")
            || htmlContent.contains("置信度")
            || htmlContent.contains("confidence");

        if (hasAiContent) {
            logger.info("✓ AI enhancement content detected in report");

            // Additional AI-specific validations
            assertTrue(htmlContent.contains("置信度") || htmlContent.contains("confidence"),
                "AI-enhanced report should show confidence metrics");
        } else {
            logger.warn("⚠ No AI content detected - AI may be disabled or API key missing");
        }

        // Verify performance (bzip2 is small, should complete quickly)
        // Allow up to 2 minutes for analysis including potential AI calls
        assertPerformanceMetrics(duration, 120_000);

        // Check for common security issue patterns in bzip2
        // bzip2 is old C code, should detect at least some issues
        boolean hasIssues = htmlContent.contains("CRITICAL")
            || htmlContent.contains("HIGH")
            || htmlContent.contains("MEDIUM")
            || htmlContent.contains("critical")
            || htmlContent.contains("high");

        if (hasIssues) {
            logger.info("✓ Security issues detected in bzip2 code");
        } else {
            logger.warn("⚠ No security issues found - analyzers may not be configured");
        }

        logger.info("E2E analyze test completed successfully");
    }

    /**
     * E2E Test 2: Rust migration advisor workflow
     * Tests: refactor rust-migration command → AI suggestion generation
     */
    @Test
    @Order(2)
    @DisplayName("E2E: Rust migration advisor command")
    void testRustMigrationCommand_E2E() throws Exception {
        logger.info("Starting E2E test: Rust migration advisor");

        // Setup - target a specific function in bzip2
        Path bzip2Project = testResourcesDir.resolve("bzip2");
        Path targetFile = bzip2Project.resolve("bzlib.c");
        int targetLine = 100; // Line number in bzlib.c

        // Verify target file exists
        assertTrue(Files.exists(targetFile), "Target file should exist: " + targetFile);

        logger.info("Target file: {}", targetFile.toAbsolutePath());
        logger.info("Target line: {}", targetLine);

        // Execute refactor rust-migration command
        long startTime = System.currentTimeMillis();

        String output = executeCommand(
            "refactor",
            "--type", "rust-migration",
            "--file", targetFile.toString(),
            "--line", String.valueOf(targetLine)
        );

        long duration = System.currentTimeMillis() - startTime;

        // Verify command execution
        assertNotNull(output, "Command output should not be null");
        logger.info("Command completed in {} ms", duration);

        // Verify output is substantial (AI should provide detailed advice)
        assertTrue(output.length() > 200,
            "Rust migration advice should be detailed (>200 chars), got: " + output.length());

        logger.info("Migration advice length: {} chars", output.length());

        // Check for expected sections in Rust migration advice
        // Based on buildRustFFIPrompt() template, we expect these sections
        String lowerOutput = output.toLowerCase();

        // Required sections from the Rust migration prompt
        boolean hasRustContent = lowerOutput.contains("rust")
            || lowerOutput.contains("ffi")
            || lowerOutput.contains("safety")
            || lowerOutput.contains("wrapper");

        boolean hasCodeSection = lowerOutput.contains("```")
            || lowerOutput.contains("code")
            || lowerOutput.contains("implementation");

        boolean hasAdviceContent = lowerOutput.contains("migration")
            || lowerOutput.contains("recommend")
            || lowerOutput.contains("suggest")
            || lowerOutput.contains("strategy");

        // Verify at least one indicator of Rust migration content
        assertTrue(hasRustContent || hasCodeSection || hasAdviceContent,
            "Output should contain Rust migration advice content");

        if (hasRustContent) {
            logger.info("✓ Rust/FFI content detected");
        }
        if (hasCodeSection) {
            logger.info("✓ Code examples detected");
        }
        if (hasAdviceContent) {
            logger.info("✓ Migration advice detected");
        }

        // Performance check - AI calls can take time but should be reasonable
        // Allow up to 1 minute for AI generation
        assertPerformanceMetrics(duration, 60_000);

        // Log a preview of the migration advice
        String preview = output.substring(0, Math.min(300, output.length()));
        logger.info("Migration advice preview:\n{}", preview);

        logger.info("E2E Rust migration test completed successfully");
    }

    /**
     * Helper: Execute CLI command and capture output
     */
    protected String executeCommand(String... args) {
        // Reset output capture
        outputCapture.reset();

        // Execute command
        int exitCode = new picocli.CommandLine(new com.harmony.agent.cli.HarmonyAgentCLI())
            .execute(args);

        // Get captured output
        String output = outputCapture.toString();

        logger.debug("Command exit code: {}", exitCode);
        logger.debug("Command output length: {} chars", output.length());

        return output;
    }

    /**
     * Helper: Verify file exists and is not empty
     */
    protected void assertFileExistsAndNotEmpty(Path file) {
        assertTrue(Files.exists(file), "File should exist: " + file);
        assertTrue(Files.isRegularFile(file), "Should be a regular file: " + file);

        try {
            long size = Files.size(file);
            assertTrue(size > 0, "File should not be empty: " + file);
            logger.debug("File size: {} bytes - {}", size, file.getFileName());
        } catch (IOException e) {
            fail("Failed to check file size: " + e.getMessage());
        }
    }

    /**
     * Helper: Verify HTML report contains expected content
     */
    protected void assertHtmlReportContains(Path reportFile, String... expectedContent) {
        try {
            String htmlContent = Files.readString(reportFile);

            for (String expected : expectedContent) {
                assertTrue(htmlContent.contains(expected),
                    "HTML report should contain: " + expected);
            }

            logger.debug("HTML report validation passed: {} checks", expectedContent.length);
        } catch (IOException e) {
            fail("Failed to read HTML report: " + e.getMessage());
        }
    }

    /**
     * Helper: Verify performance metrics
     */
    protected void assertPerformanceMetrics(long durationMs, int maxExpectedMs) {
        assertTrue(durationMs <= maxExpectedMs,
            String.format("Performance check failed: %d ms (expected <= %d ms)",
                durationMs, maxExpectedMs));
        logger.info("Performance check passed: {} ms", durationMs);
    }
}
