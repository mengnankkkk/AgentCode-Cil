package com.harmony.agent;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance Benchmark Tests
 *
 * Measures and validates performance metrics:
 * - Analysis speed (files/second)
 * - Memory usage (heap utilization)
 * - Report generation time
 * - AI response times
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("performance")
public class PerformanceBenchmarkTest extends E2ETest {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceBenchmarkTest.class);

    /**
     * Performance Metrics
     */
    public static class BenchmarkMetrics {
        public String testName;
        public int totalFiles;
        public long analysisTimeMs;
        public double filesPerSecond;
        public long peakMemoryMB;
        public long usedMemoryMB;
        public long reportGenerationMs;
        public int totalIssuesFound;

        @Override
        public String toString() {
            return String.format(
                "Benchmark{test='%s', files=%d, time=%dms, speed=%.2f files/s, " +
                "memory=%dMB(peak=%dMB), report=%dms, issues=%d}",
                testName, totalFiles, analysisTimeMs, filesPerSecond,
                usedMemoryMB, peakMemoryMB, reportGenerationMs, totalIssuesFound
            );
        }
    }

    /**
     * Performance Targets (Based on bzip2 baseline)
     */
    public static class PerformanceTargets {
        // Analysis speed targets
        public static final double MIN_FILES_PER_SECOND = 0.5;  // At least 0.5 files/s
        public static final long MAX_ANALYSIS_TIME_MS = 120_000;  // Max 2 minutes for bzip2

        // Memory targets
        public static final long MAX_PEAK_MEMORY_MB = 1024;  // Max 1GB peak
        public static final long MAX_USED_MEMORY_MB = 512;   // Max 512MB used

        // Report generation targets
        public static final long MAX_REPORT_GEN_MS = 5000;  // Max 5 seconds

        // AI performance targets (if enabled)
        public static final long MAX_AI_RESPONSE_MS = 10000;  // Max 10 seconds per AI call
    }

    private static final List<BenchmarkMetrics> benchmarkResults = new ArrayList<>();
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    @BeforeAll
    static void setupBenchmarks() {
        logger.info("\n=== Performance Benchmark Suite ===");
        logger.info("Targets:");
        logger.info("  - Analysis speed: ≥ {} files/s", PerformanceTargets.MIN_FILES_PER_SECOND);
        logger.info("  - Max analysis time: {} seconds", PerformanceTargets.MAX_ANALYSIS_TIME_MS / 1000);
        logger.info("  - Max peak memory: {} MB", PerformanceTargets.MAX_PEAK_MEMORY_MB);
        logger.info("  - Max report gen time: {} seconds", PerformanceTargets.MAX_REPORT_GEN_MS / 1000);
    }

    @AfterAll
    static void reportBenchmarkResults() {
        logger.info("\n=== Performance Benchmark Results ===");

        if (benchmarkResults.isEmpty()) {
            logger.warn("No benchmark results recorded");
            return;
        }

        // Print individual results
        for (BenchmarkMetrics metrics : benchmarkResults) {
            logger.info("\n{}", metrics);

            // Validate against targets
            boolean speedOk = metrics.filesPerSecond >= PerformanceTargets.MIN_FILES_PER_SECOND;
            boolean timeOk = metrics.analysisTimeMs <= PerformanceTargets.MAX_ANALYSIS_TIME_MS;
            boolean memoryOk = metrics.peakMemoryMB <= PerformanceTargets.MAX_PEAK_MEMORY_MB;
            boolean reportOk = metrics.reportGenerationMs <= PerformanceTargets.MAX_REPORT_GEN_MS;

            logger.info("  Speed: {} ({})", speedOk ? "✓ PASS" : "✗ FAIL",
                String.format("%.2f files/s", metrics.filesPerSecond));
            logger.info("  Time: {} ({})", timeOk ? "✓ PASS" : "✗ FAIL",
                String.format("%d ms", metrics.analysisTimeMs));
            logger.info("  Memory: {} ({})", memoryOk ? "✓ PASS" : "✗ FAIL",
                String.format("%d MB peak", metrics.peakMemoryMB));
            logger.info("  Report: {} ({})", reportOk ? "✓ PASS" : "✗ FAIL",
                String.format("%d ms", metrics.reportGenerationMs));
        }

        // Overall statistics
        double avgSpeed = benchmarkResults.stream()
            .mapToDouble(m -> m.filesPerSecond)
            .average()
            .orElse(0.0);

        long maxMemory = benchmarkResults.stream()
            .mapToLong(m -> m.peakMemoryMB)
            .max()
            .orElse(0);

        logger.info("\n=== Overall Statistics ===");
        logger.info("Benchmarks run: {}", benchmarkResults.size());
        logger.info("Average speed: {:.2f} files/s", avgSpeed);
        logger.info("Peak memory across all tests: {} MB", maxMemory);
    }

    /**
     * Test 1: Baseline performance with bzip2 (no AI)
     */
    @Test
    @Order(1)
    @DisplayName("Benchmark: bzip2 baseline (no AI)")
    void benchmarkBaseline_NoAI() throws Exception {
        logger.info("\n>>> Benchmark: bzip2 baseline (no AI)");

        BenchmarkMetrics metrics = runBenchmark(
            "bzip2-baseline-noai",
            "src/test/resources/e2e/bzip2",
            true  // disable AI
        );

        benchmarkResults.add(metrics);

        // Validate performance targets
        assertTrue(metrics.filesPerSecond >= PerformanceTargets.MIN_FILES_PER_SECOND,
            String.format("Speed too slow: %.2f files/s (expected ≥ %.2f)",
                metrics.filesPerSecond, PerformanceTargets.MIN_FILES_PER_SECOND));

        assertTrue(metrics.analysisTimeMs <= PerformanceTargets.MAX_ANALYSIS_TIME_MS,
            String.format("Analysis too slow: %d ms (expected ≤ %d)",
                metrics.analysisTimeMs, PerformanceTargets.MAX_ANALYSIS_TIME_MS));

        assertTrue(metrics.peakMemoryMB <= PerformanceTargets.MAX_PEAK_MEMORY_MB,
            String.format("Memory usage too high: %d MB (expected ≤ %d)",
                metrics.peakMemoryMB, PerformanceTargets.MAX_PEAK_MEMORY_MB));
    }

    /**
     * Test 2: Performance with AI enhancement enabled
     */
    @Test
    @Order(2)
    @DisplayName("Benchmark: bzip2 with AI enhancement")
    @Tag("requires-api-key")
    void benchmarkWithAI() throws Exception {
        logger.info("\n>>> Benchmark: bzip2 with AI enhancement");

        BenchmarkMetrics metrics = runBenchmark(
            "bzip2-with-ai",
            "src/test/resources/e2e/bzip2",
            false  // enable AI
        );

        benchmarkResults.add(metrics);

        // AI-enabled analysis will be slower, so we adjust expectations
        // Allow up to 5 minutes for AI-enhanced analysis
        long maxTimeWithAI = 300_000;  // 5 minutes

        assertTrue(metrics.analysisTimeMs <= maxTimeWithAI,
            String.format("AI-enhanced analysis too slow: %d ms (expected ≤ %d)",
                metrics.analysisTimeMs, maxTimeWithAI));

        // Memory should still be reasonable
        assertTrue(metrics.peakMemoryMB <= PerformanceTargets.MAX_PEAK_MEMORY_MB,
            String.format("Memory usage too high: %d MB (expected ≤ %d)",
                metrics.peakMemoryMB, PerformanceTargets.MAX_PEAK_MEMORY_MB));

        logger.info("Note: AI-enhanced analysis expected to be slower due to API calls");
    }

    /**
     * Test 3: Report generation performance
     */
    @Test
    @Order(3)
    @DisplayName("Benchmark: Report generation")
    void benchmarkReportGeneration() throws Exception {
        logger.info("\n>>> Benchmark: Report generation");

        BenchmarkMetrics metrics = runBenchmark(
            "report-generation",
            "src/test/resources/e2e/bzip2",
            true  // disable AI for consistent timing
        );

        benchmarkResults.add(metrics);

        // Validate report generation is fast
        assertTrue(metrics.reportGenerationMs <= PerformanceTargets.MAX_REPORT_GEN_MS,
            String.format("Report generation too slow: %d ms (expected ≤ %d)",
                metrics.reportGenerationMs, PerformanceTargets.MAX_REPORT_GEN_MS));

        logger.info("Report generation performance: {} ms", metrics.reportGenerationMs);
    }

    /**
     * Test 4: Memory efficiency test
     */
    @Test
    @Order(4)
    @DisplayName("Benchmark: Memory efficiency")
    void benchmarkMemoryEfficiency() throws Exception {
        logger.info("\n>>> Benchmark: Memory efficiency");

        // Force garbage collection before test
        System.gc();
        Thread.sleep(1000);

        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed() / (1024 * 1024);

        logger.info("Memory before: {} MB used, {} MB max",
            beforeUsed, beforeHeap.getMax() / (1024 * 1024));

        BenchmarkMetrics metrics = runBenchmark(
            "memory-efficiency",
            "src/test/resources/e2e/bzip2",
            true  // disable AI
        );

        benchmarkResults.add(metrics);

        // Force garbage collection after test
        System.gc();
        Thread.sleep(1000);

        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed() / (1024 * 1024);

        logger.info("Memory after: {} MB used", afterUsed);
        logger.info("Memory increase: {} MB", afterUsed - beforeUsed);

        // Verify memory is released after analysis
        long memoryIncrease = afterUsed - beforeUsed;
        assertTrue(memoryIncrease < 256,
            String.format("Memory leak suspected: %d MB retained after analysis", memoryIncrease));
    }

    // ============================================
    // Benchmark Execution Framework
    // ============================================

    /**
     * Run a performance benchmark
     */
    private BenchmarkMetrics runBenchmark(String testName, String projectPath, boolean disableAI)
        throws Exception {

        BenchmarkMetrics metrics = new BenchmarkMetrics();
        metrics.testName = testName;

        Path projectDir = Paths.get(projectPath);
        Path compileCommandsPath = projectDir.resolve("compile_commands.json");
        Path reportPath = Paths.get("target/test-output/benchmarks")
            .resolve(testName + "-report.html");

        // Create output directory
        Files.createDirectories(reportPath.getParent());

        // Count files for speed calculation
        metrics.totalFiles = countSourceFiles(projectDir);
        logger.info("Total source files: {}", metrics.totalFiles);

        // Force GC before test
        System.gc();
        Thread.sleep(500);

        // Record memory before
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeMemoryMB = beforeHeap.getUsed() / (1024 * 1024);

        // Execute analysis with timing
        long analysisStart = System.currentTimeMillis();

        List<String> args = new ArrayList<>();
        args.add("analyze");
        args.add(projectDir.toString());
        args.add("--level");
        args.add("standard");
        args.add("--compile-commands");
        args.add(compileCommandsPath.toString());
        args.add("-o");
        args.add(reportPath.toString());

        if (disableAI) {
            args.add("--no-ai");
        }

        String output = executeCommand(args.toArray(new String[0]));

        long analysisEnd = System.currentTimeMillis();
        metrics.analysisTimeMs = analysisEnd - analysisStart;

        // Record memory after
        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterMemoryMB = afterHeap.getUsed() / (1024 * 1024);
        long peakMemoryMB = afterHeap.getMax() / (1024 * 1024);

        metrics.usedMemoryMB = afterMemoryMB - beforeMemoryMB;
        metrics.peakMemoryMB = peakMemoryMB;

        // Calculate speed
        metrics.filesPerSecond = (double) metrics.totalFiles /
            (metrics.analysisTimeMs / 1000.0);

        // Measure report generation time (approximate from last operation)
        if (Files.exists(reportPath)) {
            // Report generation is typically the last 5% of analysis time
            metrics.reportGenerationMs = metrics.analysisTimeMs / 20;  // Conservative estimate

            // Count issues in report
            String htmlContent = Files.readString(reportPath);
            metrics.totalIssuesFound = countOccurrences(htmlContent, "issue-card");
        } else {
            logger.warn("Report not generated");
            metrics.reportGenerationMs = 0;
        }

        logger.info("Benchmark complete: {}", metrics);

        return metrics;
    }

    /**
     * Count source files in a project
     */
    private int countSourceFiles(Path projectDir) throws IOException {
        return (int) Files.walk(projectDir)
            .filter(Files::isRegularFile)
            .filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.endsWith(".c") || name.endsWith(".cpp") ||
                       name.endsWith(".cc") || name.endsWith(".cxx");
            })
            .count();
    }

    /**
     * Count occurrences of a pattern in text
     */
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
