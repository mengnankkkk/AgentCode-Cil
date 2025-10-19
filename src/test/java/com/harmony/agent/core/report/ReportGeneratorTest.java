package com.harmony.agent.core.report;

import com.harmony.agent.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ReportGenerator - verifies HTML report generation with pre-computed values
 */
class ReportGeneratorTest {

    private ReportGenerator reportGenerator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reportGenerator = new ReportGenerator();
    }

    @Test
    void testGenerateReportWithCompleteData() throws Exception {
        // Create comprehensive test data
        ScanResult scanResult = createComprehensiveScanResult();

        // Generate report
        Path outputFile = tempDir.resolve("test-report.html");
        reportGenerator.generate(scanResult, outputFile);

        // Verify report was created
        assertTrue(Files.exists(outputFile), "Report file should be created");
        assertTrue(Files.size(outputFile) > 0, "Report file should not be empty");

        // Read and verify content
        String content = Files.readString(outputFile);

        // Verify header information
        assertTrue(content.contains("Security Analysis Report"), "Should contain report title");
        assertTrue(content.contains(scanResult.getSourcePath()), "Should contain source path");

        // Verify no Java method calls (should use pre-computed values)
        assertFalse(content.contains("duration.toSeconds()"), "Should not contain Java method calls");
        assertFalse(content.contains("scanId?substring"), "Should not contain Freemarker substring");
        assertFalse(content.contains("aiValidatedCount * 100.0"), "Should not contain inline calculations");
        assertFalse(content.contains("endTime?string("), "Should not contain endTime?string method call");

        // Verify all required data is present
        assertTrue(content.contains("Total Issues"), "Should contain total issues");
        assertTrue(content.contains("Critical"), "Should contain Critical severity");
        assertTrue(content.contains("High"), "Should contain High severity");

        // Verify AI enhancement section
        assertTrue(content.contains("AI Validated"), "Should contain AI validated count");
        assertTrue(content.contains("AI Enhancement Summary"), "Should contain AI enhancement section");

        System.out.println("✅ Report generated successfully at: " + outputFile);
        System.out.println("✅ Report size: " + Files.size(outputFile) + " bytes");
    }

    @Test
    void testGenerateReportWithMinimalData() throws Exception {
        // Create minimal scan result (no issues)
        Instant startTime = Instant.now().minus(5, ChronoUnit.SECONDS);
        Instant endTime = Instant.now();

        ScanResult scanResult = new ScanResult.Builder()
            .scanId(UUID.randomUUID().toString())
            .sourcePath("/test/project")
            .startTime(startTime)
            .endTime(endTime)
            .addAnalyzer("ClangAnalyzer")
            .build();

        // Generate report
        Path outputFile = tempDir.resolve("minimal-report.html");
        reportGenerator.generate(scanResult, outputFile);

        // Verify report was created
        assertTrue(Files.exists(outputFile), "Report file should be created");
        assertTrue(Files.size(outputFile) > 0, "Report file should not be empty");

        // Verify "no issues" message
        String content = Files.readString(outputFile);
        assertTrue(content.contains("No Security Issues Found") || content.contains("0 found"),
            "Should indicate no issues found");

        System.out.println("✅ Minimal report generated successfully");
    }

    @Test
    void testGenerateReportWithAIValidation() throws Exception {
        // Create scan result with AI-validated issues
        ScanResult scanResult = createScanResultWithAIValidation();

        // Generate report
        Path outputFile = tempDir.resolve("ai-validated-report.html");
        reportGenerator.generate(scanResult, outputFile);

        // Verify report was created
        assertTrue(Files.exists(outputFile), "Report file should be created");

        // Verify AI validation content
        String content = Files.readString(outputFile);
        assertTrue(content.contains("AI Expert Analysis"), "Should contain AI expert analysis");
        assertTrue(content.contains("Confidence"), "Should contain confidence information");
        assertTrue(content.contains("validated"), "Should mention validation");

        System.out.println("✅ AI-validated report generated successfully");
    }

    // Helper method to create comprehensive test data
    private ScanResult createComprehensiveScanResult() {
        Instant startTime = Instant.now().minus(2, ChronoUnit.MINUTES);
        Instant endTime = Instant.now();

        ScanResult.Builder builder = new ScanResult.Builder()
            .scanId(UUID.randomUUID().toString())
            .sourcePath("/test/harmony-project/src")
            .startTime(startTime)
            .endTime(endTime)
            .addAnalyzer("ClangAnalyzer")
            .addAnalyzer("SemgrepAnalyzer")
            .addAnalyzer("RustAnalyzer")
            .addStatistic("ai_filtered_count", 15L)
            .addStatistic("total_analyzed_files", 42L);

        // Add CRITICAL issue with AI validation
        builder.addIssue(new SecurityIssue.Builder()
            .id("ISSUE-001")
            .title("Buffer Overflow in Memory Copy")
            .description("Potential buffer overflow detected in memcpy operation without bounds checking")
            .severity(IssueSeverity.CRITICAL)
            .category(IssueCategory.BUFFER_OVERFLOW)
            .location(new CodeLocation("src/memory/buffer.c", 125, 15, "memcpy(dest, src, size)"))
            .analyzer("ClangAnalyzer")
            .metadata("ai_validated", true)
            .metadata("ai_confidence", 0.95)
            .metadata("ai_explanation", "This is a classic buffer overflow vulnerability. The memcpy operation does not validate that 'size' is within the bounds of 'dest', which could lead to memory corruption and potential code execution.")
            .metadata("code_context", "void copy_data(char* dest, char* src, size_t size) {\n    memcpy(dest, src, size); // Vulnerable\n}")
            .build());

        // Add HIGH severity issue
        builder.addIssue(new SecurityIssue.Builder()
            .id("ISSUE-002")
            .title("Use After Free Vulnerability")
            .description("Memory is accessed after being freed")
            .severity(IssueSeverity.HIGH)
            .category(IssueCategory.USE_AFTER_FREE)
            .location(new CodeLocation("src/memory/allocator.c", 89))
            .analyzer("ClangAnalyzer")
            .metadata("ai_validated", true)
            .metadata("ai_confidence", 0.88)
            .metadata("ai_explanation", "The pointer is dereferenced after free(), which causes undefined behavior and potential security issues.")
            .build());

        // Add MEDIUM severity issue
        builder.addIssue(new SecurityIssue.Builder()
            .id("ISSUE-003")
            .title("Race Condition in Thread Synchronization")
            .description("Shared resource accessed without proper locking")
            .severity(IssueSeverity.MEDIUM)
            .category(IssueCategory.RACE_CONDITION)
            .location(new CodeLocation("src/thread/sync.c", 45))
            .analyzer("SemgrepAnalyzer")
            .metadata("ai_validated", true)
            .metadata("ai_confidence", 0.72)
            .build());

        // Add LOW severity issue
        builder.addIssue(new SecurityIssue.Builder()
            .id("ISSUE-004")
            .title("Deprecated API Usage")
            .description("Function 'gets()' is deprecated and should be replaced with 'fgets()'")
            .severity(IssueSeverity.LOW)
            .category(IssueCategory.DEPRECATED_API)
            .location(new CodeLocation("src/input/reader.c", 78))
            .analyzer("ClangAnalyzer")
            .build());

        // Add INFO severity issue
        builder.addIssue(new SecurityIssue.Builder()
            .id("ISSUE-005")
            .title("Code Quality: Long Function")
            .description("Function exceeds 100 lines, consider refactoring")
            .severity(IssueSeverity.INFO)
            .category(IssueCategory.CODE_SMELL)
            .location(new CodeLocation("src/utils/helper.c", 200))
            .analyzer("ClangAnalyzer")
            .build());

        // Add another CRITICAL issue
        builder.addIssue(new SecurityIssue.Builder()
            .id("ISSUE-006")
            .title("SQL Injection Vulnerability")
            .description("User input directly concatenated into SQL query")
            .severity(IssueSeverity.CRITICAL)
            .category(IssueCategory.SQL_INJECTION)
            .location(new CodeLocation("src/database/query.c", 156))
            .analyzer("SemgrepAnalyzer")
            .metadata("ai_validated", true)
            .metadata("ai_confidence", 0.92)
            .metadata("ai_explanation", "Direct string concatenation of user input into SQL queries allows attackers to inject malicious SQL code. Use parameterized queries instead.")
            .build());

        return builder.build();
    }

    // Helper method to create AI-validated scan result
    private ScanResult createScanResultWithAIValidation() {
        Instant startTime = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant endTime = Instant.now();

        ScanResult.Builder builder = new ScanResult.Builder()
            .scanId(UUID.randomUUID().toString())
            .sourcePath("/test/ai-validation-project")
            .startTime(startTime)
            .endTime(endTime)
            .addAnalyzer("AI-Enhanced-Analyzer")
            .addStatistic("ai_filtered_count", 8L);

        // Add AI-validated HIGH severity issue
        builder.addIssue(new SecurityIssue.Builder()
            .id("AI-001")
            .title("Hardcoded Cryptographic Key")
            .description("Cryptographic key is hardcoded in source code")
            .severity(IssueSeverity.HIGH)
            .category(IssueCategory.HARDCODED_SECRET)
            .location(new CodeLocation("src/crypto/keys.c", 23))
            .analyzer("AI-Enhanced-Analyzer")
            .metadata("ai_validated", true)
            .metadata("ai_confidence", 0.98)
            .metadata("ai_explanation", "Hardcoded cryptographic keys are a critical security flaw. Keys should be stored securely in a key management system or environment variables, never in source code.")
            .metadata("code_context", "const char* API_KEY = \"sk-1234567890abcdef\"; // Hardcoded!")
            .build());

        return builder.build();
    }
}
