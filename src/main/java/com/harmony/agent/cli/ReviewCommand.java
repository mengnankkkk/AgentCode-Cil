package com.harmony.agent.cli;

import com.harmony.agent.config.AppConfig;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.ai.CodeReviewer;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.ScanResult;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.report.JsonReportWriter;
import com.harmony.agent.core.store.StoreSession;
import com.harmony.agent.core.store.UnifiedIssueStore;
import com.harmony.agent.llm.provider.LLMProvider;
import com.harmony.agent.llm.provider.ProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Review command - AI驱动的代码审查命令
 * 灵感来自 kodus-ai
 *
 * 功能：
 * - 发现安全漏洞和潜在 bug
 * - 检查代码质量和最佳实践
 * - 分析性能问题
 * - 生成详细的审查报告
 * - 可选合并到现有分析报告
 */
@Command(
    name = "review",
    description = "AI-powered code review to find bugs, security issues, and improve code quality",
    mixinStandardHelpOptions = true
)
public class ReviewCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ReviewCommand.class);

    @ParentCommand
    private HarmonyAgentCLI parent;

    @Parameters(
        index = "0",
        description = "Source code path to review (file or directory)"
    )
    private String sourcePath;

    @Option(
        names = {"-f", "--focus"},
        description = "Review focus: all | security | performance | maintainability | best-practices | code-smells (default: all)"
    )
    private String focus = "all";

    @Option(
        names = {"-o", "--output"},
        description = "Output file path for review report"
    )
    private String outputFile;

    @Option(
        names = {"--merge-report"},
        description = "Merge review results into existing analysis report (requires -o option)"
    )
    private String mergeReportPath;

    @Option(
        names = {"--max-files"},
        description = "Maximum number of files to review (default: 50)"
    )
    private int maxFiles = 50;

    @Override
    public Integer call() {
        ConsolePrinter printer = parent.getPrinter();
        ConfigManager configManager = parent.getConfigManager();

        try {
            // 验证源路径
            Path path = Paths.get(sourcePath);
            if (!Files.exists(path)) {
                printer.error("Source path does not exist: " + sourcePath);
                return 1;
            }

            printer.header("AI-Powered Code Review");
            printer.info("Source: " + sourcePath);
            printer.info("Focus: " + focus);
            printer.blank();

            // 解析审查焦点
            CodeReviewer.ReviewFocus reviewFocus = parseReviewFocus(focus);

            // 获取配置
            AppConfig config = configManager.getConfig();
            String providerName = config.getAi().getProvider();
            String model = config.getAi().getModel();

            // 检查命令级别配置
            AppConfig.CommandConfig commandConfig = config.getAi().getCommands().get("review");
            if (commandConfig != null) {
                if (commandConfig.getProvider() != null) {
                    providerName = commandConfig.getProvider();
                }
                if (commandConfig.getModel() != null) {
                    model = commandConfig.getModel();
                }
            }

            // 解析模型别名
            model = resolveModelAlias(configManager, providerName, model);

            // 创建 LLM Provider
            String openaiKey = System.getenv("OPENAI_API_KEY");
            if (openaiKey == null || openaiKey.isEmpty()) {
                openaiKey = config.getAi().getApiKey();
            }

            String claudeKey = System.getenv("CLAUDE_API_KEY");
            String siliconflowKey = System.getenv("SILICONFLOW_API_KEY");
            ProviderFactory factory = ProviderFactory.createDefault(openaiKey, claudeKey, siliconflowKey);

            LLMProvider provider;
            try {
                provider = factory.getProvider(providerName);
            } catch (IllegalArgumentException e) {
                printer.error("LLM provider not configured: " + providerName);
                printer.blank();
                printer.info("Available providers: openai, claude, siliconflow");
                printer.info("Please set API keys (OPENAI_API_KEY, CLAUDE_API_KEY, or SILICONFLOW_API_KEY)");
                return 1;
            }

            if (!provider.isAvailable()) {
                printer.error("LLM provider not available: " + providerName);
                printer.info("Please set API key environment variable");
                return 1;
            }

            // 创建 CodeReviewer
            CodeReviewer reviewer = new CodeReviewer(provider, model);

            printer.info("AI Provider: " + provider.getProviderName());
            printer.info("Model: " + model);
            printer.blank();

            // 收集要审查的文件
            List<Path> filesToReview = collectFiles(path, printer);

            if (filesToReview.isEmpty()) {
                printer.warning("No source files found to review");
                return 0;
            }

            // 限制文件数量
            if (filesToReview.size() > maxFiles) {
                printer.warning("Found " + filesToReview.size() + " files, limiting to " + maxFiles);
                filesToReview = filesToReview.subList(0, maxFiles);
            }

            printer.info("Reviewing " + filesToReview.size() + " file(s)...");
            printer.blank();

            // 执行审查
            Instant startTime = Instant.now();

            printer.spinner("Running AI code review...", false);
            CodeReviewer.ReviewResult result = reviewer.reviewFiles(filesToReview, reviewFocus);
            printer.spinner("Running AI code review", true);

            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);

            printer.blank();

            // 显示审查结果
            displayReviewResult(printer, result);

            // 【NEW】尝试将审查结果写入 UnifiedIssueStore（如果在交互模式中）
            tryWriteToStore(printer, result);

            printer.blank();
            printer.keyValue("  Review Time", String.format("%.2f seconds", duration.toMillis() / 1000.0));
            printer.keyValue("  Files Reviewed", String.valueOf(filesToReview.size()));
            printer.blank();

            // 处理报告输出
            if (outputFile != null || mergeReportPath != null) {
                handleReportOutput(printer, result, filesToReview, duration);
            } else {
                printer.info("💡 Tip: Use -o/--output to generate a detailed review report");
            }

            printer.blank();

            // 返回码
            if (result.getCriticalCount() > 0) {
                printer.warning("⚠️  Critical issues found!");
                return 2;
            } else if (result.getHighCount() > 0) {
                printer.warning("Review completed with high-priority issues");
                return 1;
            } else {
                printer.success("✓ Code review completed");
                return 0;
            }

        } catch (Exception e) {
            printer.error("Review failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /**
     * 收集要审查的文件
     */
    private List<Path> collectFiles(Path path, ConsolePrinter printer) throws IOException {
        List<Path> files = new ArrayList<>();

        if (Files.isRegularFile(path)) {
            files.add(path);
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> paths = Files.walk(path)) {
                files = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSourceFile)
                    .sorted()
                    .collect(Collectors.toList());
            }
        }

        return files;
    }

    /**
     * 判断是否为源代码文件
     */
    private boolean isSourceFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".h") || name.endsWith(".hpp") ||
               name.endsWith(".cc") || name.endsWith(".cxx") || name.endsWith(".java") || name.endsWith(".rs") ||
               name.endsWith(".go") || name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".ts");
    }

    /**
     * 显示审查结果
     */
    private void displayReviewResult(ConsolePrinter printer, CodeReviewer.ReviewResult result) {
        printer.header("Code Review Summary");
        printer.blank();

        // 显示统计
        printer.stats("Total Issues", result.getTotalCount(), org.fusesource.jansi.Ansi.Color.CYAN);
        printer.stats("Critical", result.getCriticalCount(), org.fusesource.jansi.Ansi.Color.RED);
        printer.stats("High", result.getHighCount(), org.fusesource.jansi.Ansi.Color.RED);
        printer.stats("Medium", result.getMediumCount(), org.fusesource.jansi.Ansi.Color.YELLOW);
        printer.stats("Low", result.getLowCount(), org.fusesource.jansi.Ansi.Color.GREEN);
        printer.blank();

        // 显示详细问题
        if (result.hasIssues()) {
            printer.subheader("Issues Found");

            // 按严重程度分组
            List<SecurityIssue> criticalIssues = result.getIssues().stream()
                .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL)
                .collect(Collectors.toList());

            List<SecurityIssue> highIssues = result.getIssues().stream()
                .filter(i -> i.getSeverity() == IssueSeverity.HIGH)
                .collect(Collectors.toList());

            // 显示关键问题
            if (!criticalIssues.isEmpty()) {
                printer.blank();
                printer.error("🚨 Critical Issues:");
                for (int i = 0; i < Math.min(5, criticalIssues.size()); i++) {
                    SecurityIssue issue = criticalIssues.get(i);
                    printer.error("  " + issue.getLocation().toString());
                    printer.error("    " + issue.getTitle());
                }
                if (criticalIssues.size() > 5) {
                    printer.info("  ... and " + (criticalIssues.size() - 5) + " more critical issues");
                }
            }

            // 显示高优先级问题
            if (!highIssues.isEmpty()) {
                printer.blank();
                printer.warning("⚠️  High Priority Issues:");
                for (int i = 0; i < Math.min(5, highIssues.size()); i++) {
                    SecurityIssue issue = highIssues.get(i);
                    printer.warning("  " + issue.getLocation().toString());
                    printer.warning("    " + issue.getTitle());
                }
                if (highIssues.size() > 5) {
                    printer.info("  ... and " + (highIssues.size() - 5) + " more high priority issues");
                }
            }
        } else {
            printer.success("✓ No issues found - code looks good!");
        }
    }

    /**
     * 处理报告输出
     */
    private void handleReportOutput(ConsolePrinter printer, CodeReviewer.ReviewResult result,
                                    List<Path> files, Duration duration) {
        try {
            if (mergeReportPath != null) {
                // 合并到现有报告
                printer.blank();
                printer.info("Merging review results into existing report...");

                Path reportPath = Paths.get(mergeReportPath);
                if (!Files.exists(reportPath)) {
                    printer.error("Report file not found: " + mergeReportPath);
                    return;
                }

                // 读取现有报告
                JsonReportWriter jsonWriter = new JsonReportWriter();
                ScanResult existingResult = jsonWriter.read(reportPath);

                // 添加审查问题
                List<SecurityIssue> mergedIssues = new ArrayList<>(existingResult.getIssues());
                mergedIssues.addAll(result.getIssues());

                // 创建新的 ScanResult 使用 Builder 模式
                ScanResult.Builder builder = new ScanResult.Builder()
                    .sourcePath(existingResult.getSourcePath())
                    .startTime(existingResult.getStartTime())
                    .endTime(existingResult.getEndTime().plus(duration))
                    .addIssues(mergedIssues);

                // 添加统计信息
                existingResult.getStatistics().forEach(builder::addStatistic);

                // 添加分析器
                existingResult.getAnalyzersUsed().forEach(builder::addAnalyzer);

                ScanResult mergedResult = builder.build();

                // 写入报告
                String outputPath = outputFile != null ? outputFile : mergeReportPath;
                jsonWriter.write(mergedResult, Paths.get(outputPath));

                printer.success("✓ Review results merged into: " + outputPath);

            } else if (outputFile != null) {
                // 创建新报告
                printer.blank();
                printer.info("Generating review report...");

                // 计算时间戳
                Instant now = Instant.now();
                Instant start = now.minus(duration);

                // 创建 ScanResult 使用 Builder 模式
                ScanResult scanResult = new ScanResult.Builder()
                    .sourcePath(sourcePath)
                    .startTime(start)
                    .endTime(now)
                    .addIssues(result.getIssues())
                    .addStatistic("total_files", files.size())
                    .addAnalyzer("CodeReviewer")
                    .build();

                // 写入报告
                JsonReportWriter jsonWriter = new JsonReportWriter();
                jsonWriter.write(scanResult, Paths.get(outputFile));

                printer.success("✓ Review report generated: " + outputFile);
            }

        } catch (Exception e) {
            printer.error("Failed to generate report: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 解析审查焦点
     */
    private CodeReviewer.ReviewFocus parseReviewFocus(String focus) {
        return switch (focus.toLowerCase()) {
            case "security" -> CodeReviewer.ReviewFocus.SECURITY;
            case "performance" -> CodeReviewer.ReviewFocus.PERFORMANCE;
            case "maintainability" -> CodeReviewer.ReviewFocus.MAINTAINABILITY;
            case "best-practices" -> CodeReviewer.ReviewFocus.BEST_PRACTICES;
            case "code-smells" -> CodeReviewer.ReviewFocus.CODE_SMELLS;
            default -> CodeReviewer.ReviewFocus.ALL;
        };
    }

    /**
     * 尝试将审查结果写入 UnifiedIssueStore
     * 只在交互模式中有效；独立运行时安全地跳过
     */
    private void tryWriteToStore(ConsolePrinter printer, CodeReviewer.ReviewResult result) {
        try {
            // 尝试从 parent (HarmonyAgentCLI) 中获取 storeSession（通过反射）
            java.lang.reflect.Field storeSessionField = HarmonyAgentCLI.class.getDeclaredField("storeSession");
            storeSessionField.setAccessible(true);
            Object storeSessionObj = storeSessionField.get(parent);

            if (storeSessionObj != null && storeSessionObj instanceof StoreSession) {
                StoreSession session = (StoreSession) storeSessionObj;
                UnifiedIssueStore store = session.getStore();

                // 将审查结果添加到 Store
                store.addIssues(result.getIssues());
                printer.info("💾 Review results added to unified store (" + result.getIssues().size() + " issues)");
            }
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            // 不在交互模式中或 storeSession 不可用，安全地跳过
            logger.debug("Not in interactive mode or StoreSession not available: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Failed to write review results to store: {}", e.getMessage());
        }
    }

    /**
     * 解析模型别名
     */
    private String resolveModelAlias(ConfigManager configManager, String providerName, String modelAlias) {
        if (modelAlias == null || modelAlias.isEmpty()) {
            return modelAlias;
        }

        // Check if it's already a full model name
        if (modelAlias.contains("/") || modelAlias.contains("-")) {
            return modelAlias;
        }

        // Try to resolve from provider models configuration
        try {
            var providers = configManager.getConfig().getAi().getProviders();
            if (providers != null && providers.containsKey(providerName)) {
                var providerConfig = providers.get(providerName);
                var models = providerConfig.getModels();
                if (models != null && models.containsKey(modelAlias)) {
                    String resolvedModel = models.get(modelAlias);
                    logger.debug("Resolved model alias '{}' to '{}' for provider '{}'",
                        modelAlias, resolvedModel, providerName);
                    return resolvedModel;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to resolve model alias '{}': {}", modelAlias, e.getMessage());
        }

        return modelAlias;
    }
}
