package com.harmony.agent.core;

import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.analyzer.Analyzer;
import com.harmony.agent.core.analyzer.AnalyzerException;
import com.harmony.agent.core.analyzer.ClangAnalyzer;
import com.harmony.agent.core.analyzer.SemgrepAnalyzer;
import com.harmony.agent.core.analyzer.RegexAnalyzer;
import com.harmony.agent.core.model.ScanResult;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.scanner.CodeScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Main analysis engine that coordinates scanning and analysis
 */
public class AnalysisEngine {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisEngine.class);

    private final String sourcePath;
    private final AnalysisConfig config;
    private final CodeScanner scanner;
    private final List<Analyzer> analyzers;
    private final ExecutorService executorService;

    public AnalysisEngine(String sourcePath, AnalysisConfig config) {
        this.sourcePath = sourcePath;
        this.config = config;
        this.scanner = new CodeScanner(sourcePath, true);
        this.analyzers = initializeAnalyzers();

        // Create thread pool for parallel analysis
        int threadCount = config.isParallel() ? config.getMaxThreads() : 1;
        this.executorService = Executors.newFixedThreadPool(threadCount);

        logger.info("AnalysisEngine initialized with {} threads", threadCount);
    }

    /**
     * Initialize available analyzers
     */
    private List<Analyzer> initializeAnalyzers() {
        List<Analyzer> analyzers = new ArrayList<>();

        // Add Clang analyzer if available
        ClangAnalyzer clangAnalyzer = new ClangAnalyzer();
        if (clangAnalyzer.isAvailable()) {
            analyzers.add(clangAnalyzer);
            logger.info("Clang analyzer enabled: {}", clangAnalyzer.getVersion());
        } else {
            logger.warn("Clang analyzer not available - ensure clang-tidy is installed");
        }

        // Add Semgrep analyzer if available
        SemgrepAnalyzer semgrepAnalyzer = new SemgrepAnalyzer();
        if (semgrepAnalyzer.isAvailable()) {
            analyzers.add(semgrepAnalyzer);
            logger.info("Semgrep analyzer enabled: {}", semgrepAnalyzer.getVersion());
        } else {
            logger.warn("Semgrep analyzer not available - ensure semgrep is installed");
        }

        // Always add built-in regex analyzer as fallback
        RegexAnalyzer regexAnalyzer = new RegexAnalyzer();
        analyzers.add(regexAnalyzer);
        logger.info("Built-in regex analyzer enabled: {}", regexAnalyzer.getVersion());

        return analyzers;
    }

    /**
     * Run full analysis
     */
    public ScanResult analyze() throws IOException, AnalyzerException {
        logger.info("Starting analysis of: {}", sourcePath);
        Instant startTime = Instant.now();

        // Scan for files
        List<Path> files = config.isIncremental() ?
            scanner.scanIncremental() : scanner.scanAll();

        logger.info("Scanning complete. Found {} files to analyze", files.size());

        if (files.isEmpty()) {
            logger.warn("No files found to analyze");
            return createEmptyResult(startTime);
        }

        // Analyze files
        List<SecurityIssue> allIssues = analyzeFiles(files);

        // Deduplicate issues
        allIssues = deduplicateIssues(allIssues);

        // Build result
        Instant endTime = Instant.now();
        ScanResult.Builder resultBuilder = new ScanResult.Builder()
            .sourcePath(sourcePath)
            .startTime(startTime)
            .endTime(endTime)
            .addIssues(allIssues);

        // Add analyzers used
        for (Analyzer analyzer : analyzers) {
            resultBuilder.addAnalyzer(analyzer.getName());
        }

        // Add statistics
        resultBuilder.addStatistic("total_files", files.size());
        resultBuilder.addStatistic("total_issues", allIssues.size());
        resultBuilder.addStatistic("analyzers_count", analyzers.size());

        ScanResult result = resultBuilder.build();

        logger.info("Analysis complete. Found {} issues in {} files",
            allIssues.size(), files.size());

        return result;
    }

    /**
     * Analyze files with available analyzers
     */
    private List<SecurityIssue> analyzeFiles(List<Path> files) throws AnalyzerException {
        if (analyzers.isEmpty()) {
            throw new AnalyzerException("No analyzers available");
        }

        List<SecurityIssue> allIssues = new ArrayList<>();

        if (config.isParallel()) {
            // Parallel analysis
            allIssues = analyzeParallel(files);
        } else {
            // Sequential analysis
            allIssues = analyzeSequential(files);
        }

        return allIssues;
    }

    /**
     * Sequential file analysis
     */
    private List<SecurityIssue> analyzeSequential(List<Path> files) {
        List<SecurityIssue> allIssues = new ArrayList<>();

        for (Analyzer analyzer : analyzers) {
            logger.info("Running analyzer: {}", analyzer.getName());

            for (Path file : files) {
                try {
                    List<SecurityIssue> issues = analyzer.analyze(file);
                    allIssues.addAll(issues);
                } catch (AnalyzerException e) {
                    logger.error("Analysis failed for {}: {}", file, e.getMessage());
                }
            }
        }

        return allIssues;
    }

    /**
     * Parallel file analysis
     */
    private List<SecurityIssue> analyzeParallel(List<Path> files) {
        List<Future<List<SecurityIssue>>> futures = new ArrayList<>();

        // Submit analysis tasks for each analyzer
        for (Analyzer analyzer : analyzers) {
            logger.info("Running analyzer in parallel: {}", analyzer.getName());

            Future<List<SecurityIssue>> future = executorService.submit(() -> {
                List<SecurityIssue> issues = new ArrayList<>();
                for (Path file : files) {
                    try {
                        issues.addAll(analyzer.analyze(file));
                    } catch (AnalyzerException e) {
                        logger.error("Analysis failed for {}: {}", file, e.getMessage());
                    }
                }
                return issues;
            });

            futures.add(future);
        }

        // Collect results
        List<SecurityIssue> allIssues = new ArrayList<>();
        for (Future<List<SecurityIssue>> future : futures) {
            try {
                List<SecurityIssue> issues = future.get(config.getTimeout(), TimeUnit.SECONDS);
                allIssues.addAll(issues);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Failed to get analysis results", e);
            }
        }

        return allIssues;
    }

    /**
     * Deduplicate issues based on hash
     */
    private List<SecurityIssue> deduplicateIssues(List<SecurityIssue> issues) {
        Map<String, SecurityIssue> uniqueIssues = new LinkedHashMap<>();

        for (SecurityIssue issue : issues) {
            String hash = issue.getHash();
            if (!uniqueIssues.containsKey(hash)) {
                uniqueIssues.put(hash, issue);
            }
        }

        int duplicateCount = issues.size() - uniqueIssues.size();
        if (duplicateCount > 0) {
            logger.info("Removed {} duplicate issues", duplicateCount);
        }

        return new ArrayList<>(uniqueIssues.values());
    }

    /**
     * Create empty result (no files found)
     */
    private ScanResult createEmptyResult(Instant startTime) {
        return new ScanResult.Builder()
            .sourcePath(sourcePath)
            .startTime(startTime)
            .endTime(Instant.now())
            .addStatistic("total_files", 0)
            .addStatistic("total_issues", 0)
            .build();
    }

    /**
     * Shutdown executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get available analyzers
     */
    public List<Analyzer> getAnalyzers() {
        return Collections.unmodifiableList(analyzers);
    }

    /**
     * Analysis configuration
     */
    public static class AnalysisConfig {
        private final String level;
        private final boolean incremental;
        private final boolean parallel;
        private final int maxThreads;
        private final int timeout;

        public AnalysisConfig(String level, boolean incremental, boolean parallel,
                             int maxThreads, int timeout) {
            this.level = level;
            this.incremental = incremental;
            this.parallel = parallel;
            this.maxThreads = maxThreads;
            this.timeout = timeout;
        }

        public static AnalysisConfig fromConfigManager() {
            ConfigManager configManager = ConfigManager.getInstance();
            return new AnalysisConfig(
                configManager.getConfig().getAnalysis().getLevel(),
                configManager.getConfig().getAnalysis().isIncremental(),
                configManager.getConfig().getAnalysis().isParallel(),
                configManager.getConfig().getAnalysis().getMaxThreads(),
                configManager.getConfig().getAnalysis().getTimeout()
            );
        }

        public String getLevel() {
            return level;
        }

        public boolean isIncremental() {
            return incremental;
        }

        public boolean isParallel() {
            return parallel;
        }

        public int getMaxThreads() {
            return maxThreads;
        }

        public int getTimeout() {
            return timeout;
        }
    }
}
