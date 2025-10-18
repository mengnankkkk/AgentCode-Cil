package com.harmony.agent;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenHarmony Graded Library Testing Framework
 *
 * Tests HarmonySafeAgent capabilities against OpenHarmony libraries with varying difficulty levels:
 * - ★★☆ Basic: Simple memory safety issues
 * - ★★★ Medium: Concurrency, FFI, and cross-language issues
 * - ★★★★ Hard: Complex lifecycle and async safety issues
 * - ★★★★★ Very Hard: Hybrid architecture and security-critical code
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Requires OpenHarmony library setup - enable when ready")
public class GradedLibraryTest extends E2ETest {

    private static final Logger logger = LoggerFactory.getLogger(GradedLibraryTest.class);

    /**
     * Test Library Metadata
     */
    public static class LibraryInfo {
        public final String name;
        public final String difficulty;
        public final int difficultyStars;
        public final String gitUrl;
        public final String focusArea;
        public final int expectedMinIssues;

        public LibraryInfo(String name, String difficulty, int difficultyStars,
                          String gitUrl, String focusArea, int expectedMinIssues) {
            this.name = name;
            this.difficulty = difficulty;
            this.difficultyStars = difficultyStars;
            this.gitUrl = gitUrl;
            this.focusArea = focusArea;
            this.expectedMinIssues = expectedMinIssues;
        }
    }

    /**
     * Test Result Metrics
     */
    public static class TestMetrics {
        public String libraryName;
        public long analysisTimeMs;
        public int totalIssues;
        public int criticalIssues;
        public int highIssues;
        public int aiValidatedIssues;
        public boolean reportGenerated;
        public boolean passedTests;

        @Override
        public String toString() {
            return String.format(
                "TestMetrics{library='%s', time=%dms, issues=%d (critical=%d, high=%d), " +
                "AI-validated=%d, report=%s, passed=%s}",
                libraryName, analysisTimeMs, totalIssues, criticalIssues, highIssues,
                aiValidatedIssues, reportGenerated, passedTests
            );
        }
    }

    // Library definitions based on TEST_LIBRARIES.md
    private static final List<LibraryInfo> LIBRARIES = List.of(
        // ★★☆ Basic Level
        new LibraryInfo(
            "commonlibrary_c_utils",
            "Basic",
            2,
            "https://github.com/openharmony/commonlibrary_c_utils",
            "Basic memory safety",
            10
        ),

        // ★★★ Medium Level
        new LibraryInfo(
            "hiviewdfx_hilog",
            "Medium",
            3,
            "https://github.com/openharmony/hiviewdfx_hilog",
            "Concurrency safety",
            15
        ),
        new LibraryInfo(
            "request_request",
            "Medium",
            3,
            "https://github.com/openharmony/request_request",
            "Rust unsafe optimization",
            10
        ),
        new LibraryInfo(
            "hisysevent",
            "Medium",
            3,
            "https://gitee.com/openharmony/hiviewdfx_hisysevent",
            "Cross-language optimization",
            12
        ),

        // ★★★★ Hard Level
        new LibraryInfo(
            "communication_ipc",
            "Hard",
            4,
            "https://github.com/openharmony/communication_ipc",
            "Lifecycle management",
            20
        ),
        new LibraryInfo(
            "ylong_runtime",
            "Hard",
            4,
            "https://gitee.com/openharmony/commonlibrary_rust_ylong_runtime",
            "Async safety",
            15
        ),

        // ★★★★★ Very Hard Level
        new LibraryInfo(
            "security_asset",
            "Very Hard",
            5,
            "https://github.com/openharmony/security_asset",
            "Hybrid architecture design",
            25
        )
    );

    private static final List<TestMetrics> testResults = new ArrayList<>();
    private static Path librariesDir;

    @BeforeAll
    static void setupGradedTests() throws IOException {
        logger.info("=== OpenHarmony Graded Library Testing Framework ===");
        logger.info("Total libraries: {}", LIBRARIES.size());

        librariesDir = Paths.get("src/test/resources/e2e/openharmony");

        // Create directory if needed
        if (!Files.exists(librariesDir)) {
            Files.createDirectories(librariesDir);
            logger.info("Created OpenHarmony libraries directory: {}", librariesDir.toAbsolutePath());
        }

        // Log library information
        for (LibraryInfo lib : LIBRARIES) {
            logger.info("  {} {} - {} (Expected issues: ≥{})",
                "★".repeat(lib.difficultyStars),
                lib.name,
                lib.focusArea,
                lib.expectedMinIssues
            );
        }
    }

    @AfterAll
    static void reportGradedResults() {
        logger.info("\n=== Graded Library Test Results ===");

        if (testResults.isEmpty()) {
            logger.warn("No test results recorded");
            return;
        }

        // Summary by difficulty level
        for (int stars = 2; stars <= 5; stars++) {
            final int difficultyLevel = stars;
            List<TestMetrics> levelResults = testResults.stream()
                .filter(m -> getLibraryByName(m.libraryName)
                    .map(lib -> lib.difficultyStars == difficultyLevel)
                    .orElse(false))
                .toList();

            if (!levelResults.isEmpty()) {
                logger.info("\n{} Difficulty ({}★):",
                    getDifficultyName(stars),
                    "★".repeat(stars));

                for (TestMetrics metrics : levelResults) {
                    logger.info("  - {}", metrics);
                }
            }
        }

        // Overall statistics
        long totalTime = testResults.stream().mapToLong(m -> m.analysisTimeMs).sum();
        int totalIssues = testResults.stream().mapToInt(m -> m.totalIssues).sum();
        long passedTests = testResults.stream().filter(m -> m.passedTests).count();

        logger.info("\n=== Overall Statistics ===");
        logger.info("Libraries tested: {}", testResults.size());
        logger.info("Tests passed: {}/{}", passedTests, testResults.size());
        logger.info("Total issues found: {}", totalIssues);
        logger.info("Total analysis time: {} seconds", totalTime / 1000.0);
    }

    /**
     * Test framework: Analyze a specific OpenHarmony library
     */
    protected TestMetrics testLibrary(LibraryInfo library) throws Exception {
        logger.info("\n>>> Testing: {} ({}★ - {})",
            library.name,
            "★".repeat(library.difficultyStars),
            library.difficulty
        );

        TestMetrics metrics = new TestMetrics();
        metrics.libraryName = library.name;

        Path libraryPath = librariesDir.resolve(library.name);
        Path reportPath = Paths.get("target/test-output/graded")
            .resolve(library.name + "-report.html");

        // Check if library exists
        if (!Files.exists(libraryPath)) {
            logger.warn("Library not found: {}", libraryPath);
            logger.info("Expected path: {}", libraryPath.toAbsolutePath());
            logger.info("Clone with: git clone {} {}",
                library.gitUrl, libraryPath);
            metrics.passedTests = false;
            return metrics;
        }

        // Find compile_commands.json
        Path compileCommandsPath = findCompileCommands(libraryPath);
        if (compileCommandsPath == null) {
            logger.warn("compile_commands.json not found in {}", libraryPath);
            logger.info("Generate with: bear -- make (in library directory)");
            metrics.passedTests = false;
            return metrics;
        }

        // Ensure output directory exists
        Files.createDirectories(reportPath.getParent());

        // Execute analysis
        long startTime = System.currentTimeMillis();

        String output = executeCommand(
            "analyze",
            libraryPath.toString(),
            "--level", "standard",
            "--compile-commands", compileCommandsPath.toString(),
            "-o", reportPath.toString()
        );

        metrics.analysisTimeMs = System.currentTimeMillis() - startTime;

        // Verify report generation
        if (Files.exists(reportPath)) {
            metrics.reportGenerated = true;

            // Parse report for metrics
            String htmlContent = Files.readString(reportPath);
            metrics.totalIssues = countOccurrences(htmlContent, "issue-card");
            metrics.criticalIssues = countOccurrences(htmlContent, "severity-critical");
            metrics.highIssues = countOccurrences(htmlContent, "severity-high");
            metrics.aiValidatedIssues = countOccurrences(htmlContent, "🤖");

            logger.info("Analysis completed: {} issues found in {} seconds",
                metrics.totalIssues,
                metrics.analysisTimeMs / 1000.0
            );
            logger.info("  - Critical: {}, High: {}, AI-validated: {}",
                metrics.criticalIssues,
                metrics.highIssues,
                metrics.aiValidatedIssues
            );
        } else {
            logger.error("Report not generated at: {}", reportPath);
            metrics.reportGenerated = false;
        }

        // Validate against expectations
        metrics.passedTests = metrics.reportGenerated &&
            metrics.totalIssues >= library.expectedMinIssues;

        if (metrics.passedTests) {
            logger.info("✓ Test PASSED for {}", library.name);
        } else {
            logger.warn("✗ Test FAILED for {}: Expected ≥{} issues, found {}",
                library.name,
                library.expectedMinIssues,
                metrics.totalIssues
            );
        }

        return metrics;
    }

    // ============================================
    // Test Cases for Each Difficulty Level
    // ============================================

    @Test
    @Order(1)
    @DisplayName("★★☆ Basic: commonlibrary_c_utils")
    void testBasicLevel_CUtils() throws Exception {
        LibraryInfo lib = getLibraryByName("commonlibrary_c_utils").orElseThrow();
        TestMetrics metrics = testLibrary(lib);
        testResults.add(metrics);

        assertTrue(metrics.passedTests,
            "Basic level library should detect expected issues");
    }

    @Test
    @Order(2)
    @DisplayName("★★★ Medium: hiviewdfx_hilog")
    void testMediumLevel_Hilog() throws Exception {
        LibraryInfo lib = getLibraryByName("hiviewdfx_hilog").orElseThrow();
        TestMetrics metrics = testLibrary(lib);
        testResults.add(metrics);

        assertTrue(metrics.passedTests,
            "Medium level library (concurrency) should detect expected issues");
    }

    @Test
    @Order(3)
    @DisplayName("★★★ Medium: request_request")
    void testMediumLevel_Request() throws Exception {
        LibraryInfo lib = getLibraryByName("request_request").orElseThrow();
        TestMetrics metrics = testLibrary(lib);
        testResults.add(metrics);

        assertTrue(metrics.passedTests,
            "Medium level library (Rust unsafe) should detect expected issues");
    }

    @Test
    @Order(4)
    @DisplayName("★★★★ Hard: communication_ipc")
    void testHardLevel_IPC() throws Exception {
        LibraryInfo lib = getLibraryByName("communication_ipc").orElseThrow();
        TestMetrics metrics = testLibrary(lib);
        testResults.add(metrics);

        assertTrue(metrics.passedTests,
            "Hard level library (lifecycle) should detect expected issues");
    }

    @Test
    @Order(5)
    @DisplayName("★★★★★ Very Hard: security_asset")
    void testVeryHardLevel_SecurityAsset() throws Exception {
        LibraryInfo lib = getLibraryByName("security_asset").orElseThrow();
        TestMetrics metrics = testLibrary(lib);
        testResults.add(metrics);

        assertTrue(metrics.passedTests,
            "Very hard level library (hybrid architecture) should detect expected issues");
    }

    // ============================================
    // Helper Methods
    // ============================================

    private static java.util.Optional<LibraryInfo> getLibraryByName(String name) {
        return LIBRARIES.stream()
            .filter(lib -> lib.name.equals(name))
            .findFirst();
    }

    private static String getDifficultyName(int stars) {
        return switch (stars) {
            case 2 -> "Basic";
            case 3 -> "Medium";
            case 4 -> "Hard";
            case 5 -> "Very Hard";
            default -> "Unknown";
        };
    }

    private Path findCompileCommands(Path libraryPath) throws IOException {
        // Check root directory
        Path rootCompileCommands = libraryPath.resolve("compile_commands.json");
        if (Files.exists(rootCompileCommands)) {
            return rootCompileCommands;
        }

        // Check build directories
        String[] buildDirs = {"build", "out", "cmake-build-debug", "cmake-build-release"};
        for (String dir : buildDirs) {
            Path buildCompileCommands = libraryPath.resolve(dir).resolve("compile_commands.json");
            if (Files.exists(buildCompileCommands)) {
                return buildCompileCommands;
            }
        }

        return null;
    }

    private int countOccurrences(String content, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
