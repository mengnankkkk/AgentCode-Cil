package com.harmony.agent.core.report;

import com.harmony.agent.core.AnalysisEngine;
import com.harmony.agent.core.model.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test report generation with REAL scan data from actual e2e test projects
 * This will expose any real issues with the template
 */
class RealDataReportTest {

    private ReportGenerator reportGenerator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reportGenerator = new ReportGenerator();
    }

    @Test
    void testGenerateReportFromRealBzip2Scan() throws Exception {
        // Use real e2e test project
        Path bzip2Path = Paths.get("src/test/resources/e2e/bzip2");

        if (!Files.exists(bzip2Path)) {
            System.out.println("⚠️ Bzip2 test project not found, skipping test");
            return;
        }

        System.out.println("📂 Scanning real project: " + bzip2Path);

        // Create analysis engine with real configuration
        AnalysisEngine.AnalysisConfig config = AnalysisEngine.AnalysisConfig.fromConfigManager();
        AnalysisEngine analysisEngine = new AnalysisEngine(bzip2Path.toString(), config);

        // Perform REAL scan
        ScanResult scanResult = analysisEngine.analyze();

        System.out.println("✅ Scan completed:");
        System.out.println("   - Total issues: " + scanResult.getTotalIssueCount());
        System.out.println("   - Duration: " + scanResult.getDuration());
        System.out.println("   - Analyzers: " + scanResult.getAnalyzersUsed());
        System.out.println("   - Critical issues: " + (scanResult.hasCriticalIssues() ? "YES" : "NO"));

        // Generate report with REAL data
        Path outputFile = tempDir.resolve("real-bzip2-report.html");
        System.out.println("\n📝 Generating report at: " + outputFile);

        try {
            reportGenerator.generate(scanResult, outputFile);

            // Verify report was created
            assertTrue(Files.exists(outputFile), "Report file should be created");
            assertTrue(Files.size(outputFile) > 0, "Report file should not be empty");

            System.out.println("✅ Report generated successfully!");
            System.out.println("   - File size: " + Files.size(outputFile) + " bytes");
            System.out.println("   - Location: " + outputFile);

            // Verify content
            String content = Files.readString(outputFile);
            assertTrue(content.contains("Security Analysis Report"), "Should contain report title");
            assertTrue(content.contains(bzip2Path.toString()) || content.contains("bzip2"),
                "Should contain source path");

            System.out.println("\n✅ All assertions passed!");

        } catch (Exception e) {
            System.err.println("\n❌ Report generation FAILED with real data:");
            System.err.println("   Error: " + e.getClass().getSimpleName());
            System.err.println("   Message: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("   Cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    void testGenerateReportFromRealCommonLibrary() throws Exception {
        // Use another real e2e test project
        Path projectPath = Paths.get("src/test/resources/e2e/commonlibrary_c_utils");

        if (!Files.exists(projectPath)) {
            System.out.println("⚠️ commonlibrary_c_utils not found, skipping test");
            return;
        }

        System.out.println("📂 Scanning real project: " + projectPath);

        // Create analysis engine
        AnalysisEngine.AnalysisConfig config = AnalysisEngine.AnalysisConfig.fromConfigManager();
        AnalysisEngine analysisEngine = new AnalysisEngine(projectPath.toString(), config);

        // Perform REAL scan
        ScanResult scanResult = analysisEngine.analyze();

        System.out.println("✅ Scan completed:");
        System.out.println("   - Total issues: " + scanResult.getTotalIssueCount());
        System.out.println("   - Duration: " + scanResult.getDuration());

        // Generate report with REAL data
        Path outputFile = tempDir.resolve("real-commonlib-report.html");
        System.out.println("\n📝 Generating report at: " + outputFile);

        try {
            reportGenerator.generate(scanResult, outputFile);

            assertTrue(Files.exists(outputFile), "Report file should be created");
            assertTrue(Files.size(outputFile) > 0, "Report file should not be empty");

            System.out.println("✅ Report generated successfully!");
            System.out.println("   - File size: " + Files.size(outputFile) + " bytes");

        } catch (Exception e) {
            System.err.println("\n❌ Report generation FAILED:");
            System.err.println("   Error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
