package com.harmony.agent.core.report;

import com.harmony.agent.core.model.ScanResult;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * HTML Report Generator - Creates professional security analysis reports
 * Uses Freemarker template engine for flexible report generation
 */
public class ReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);

    private final Configuration freemarkerConfig;

    /**
     * Constructor - Initializes Freemarker configuration
     */
    public ReportGenerator() {
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);

        // Load templates from classpath
        freemarkerConfig.setClassForTemplateLoading(ReportGenerator.class, "/templates");
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarkerConfig.setLogTemplateExceptions(false);
        freemarkerConfig.setWrapUncheckedExceptions(true);
        freemarkerConfig.setFallbackOnNullLoopVariable(false);

        logger.info("ReportGenerator initialized with Freemarker {}", Configuration.VERSION_2_3_32);
    }

    /**
     * Generate HTML report from scan result
     *
     * @param result ScanResult containing all security issues
     * @param outputFile Path to output HTML file
     * @throws IOException if file writing fails
     * @throws TemplateException if template processing fails
     */
    public void generate(ScanResult result, Path outputFile) throws IOException, TemplateException {
        logger.info("Generating HTML report at: {}", outputFile);

        // 1. Create data model for template
        Map<String, Object> dataModel = createDataModel(result);

        // 2. Load template
        Template template = freemarkerConfig.getTemplate("report.ftlh");

        // 3. Generate report
        try (Writer fileWriter = new FileWriter(outputFile.toFile())) {
            template.process(dataModel, fileWriter);
        }

        logger.info("Report generated successfully: {} issues, {} bytes",
            result.getTotalIssueCount(),
            outputFile.toFile().length());
    }

    /**
     * Create data model for Freemarker template
     *
     * @param result ScanResult
     * @return Data model map
     */
    private Map<String, Object> createDataModel(ScanResult result) {
        Map<String, Object> data = new HashMap<>();

        // Basic scan information with null safety
        data.put("scanId", result.getScanId() != null ? result.getScanId() : "unknown");
        data.put("sourcePath", result.getSourcePath() != null ? result.getSourcePath() : "N/A");
        data.put("startTime", result.getStartTime() != null ? result.getStartTime() : java.time.Instant.now());
        data.put("endTime", result.getEndTime() != null ? result.getEndTime() : java.time.Instant.now());
        data.put("duration", result.getDuration() != null ? result.getDuration() : java.time.Duration.ZERO);

        // Issues - Convert all counts to Long for Freemarker type consistency
        data.put("issues", result.getIssues());
        data.put("totalIssues", Long.valueOf(result.getTotalIssueCount()));

        // Statistics
        data.put("statistics", result.getStatistics());
        data.put("analyzersUsed", result.getAnalyzersUsed());

        // CRITICAL FIX: Convert enum keys to String keys for template compatibility
        // Freemarker cannot match enum objects with string literals like 'CRITICAL'
        Map<String, Long> severityCountsStringKey = new HashMap<>();
        result.getIssueCountBySeverity().forEach((severity, count) -> {
            severityCountsStringKey.put(severity.name(), count);
        });
        data.put("severityCounts", severityCountsStringKey);

        Map<String, Long> categoryCountsStringKey = new HashMap<>();
        result.getIssueCountByCategory().forEach((category, count) -> {
            categoryCountsStringKey.put(category.name(), count);
        });
        data.put("categoryCounts", categoryCountsStringKey);

        // Critical issues
        data.put("hasCriticalIssues", result.hasCriticalIssues());
        data.put("criticalIssues", result.getIssuesBySeverity(
            com.harmony.agent.core.model.IssueSeverity.CRITICAL));

        // AI enhancement status - Ensure Long type for arithmetic operations in template
        long aiValidatedCount = result.getIssues().stream()
            .filter(issue -> {
                Object validated = issue.getMetadata().get("ai_validated");
                return validated != null && validated.equals(true);
            })
            .count();
        data.put("aiValidatedCount", Long.valueOf(aiValidatedCount));

        // Calculate AI filtering stats - Safe type conversion for Integer/Long compatibility
        Object aiFilteredValue = result.getStatistics().getOrDefault("ai_filtered_count", 0L);
        long aiFilteredCount = (aiFilteredValue instanceof Number)
            ? ((Number) aiFilteredValue).longValue()
            : 0L;
        data.put("aiFilteredCount", Long.valueOf(aiFilteredCount));

        return data;
    }
}
