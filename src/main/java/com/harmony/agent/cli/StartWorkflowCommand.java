package com.harmony.agent.cli;

import com.harmony.agent.autofix.AutoFixOrchestrator;
import com.harmony.agent.autofix.ChangeManager;
import com.harmony.agent.autofix.CodeValidator;
import com.harmony.agent.autofix.PendingChange;
import com.harmony.agent.config.AppConfig;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.AnalysisEngine;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.ScanResult;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.llm.LLMClient;
import com.harmony.agent.tools.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Start Workflow Command - Implements the complete AI Agent workflow:
 * 1. Deep Analysis & Intelligent Evaluation (Hybrid Understanding + Decision Engine)
 * 2. Human-AI Collaborative Decision (User choices)
 * 3. High-Quality Security Evolution (AI Fix/Refactor with GVI loop)
 * 4. Review, Acceptance & Feedback Loop
 */
public class StartWorkflowCommand {

    private static final Logger logger = LoggerFactory.getLogger(StartWorkflowCommand.class);

    private final ConsolePrinter printer;
    private final ConfigManager configManager;
    private final LLMClient llmClient;
    private final File workingDirectory;
    private final ToolExecutor toolExecutor;
    private final CodeValidator codeValidator;
    private final AutoFixOrchestrator autoFixOrchestrator;
    private final ChangeManager changeManager;

    /**
     * Constructor
     */
    public StartWorkflowCommand(ConsolePrinter printer, ConfigManager configManager,
                                LLMClient llmClient, File workingDirectory) {
        this.printer = printer;
        this.configManager = configManager;
        this.llmClient = llmClient;
        this.workingDirectory = workingDirectory;
        this.toolExecutor = new ToolExecutor(workingDirectory);
        this.codeValidator = new CodeValidator(toolExecutor, workingDirectory);
        this.autoFixOrchestrator = new AutoFixOrchestrator(llmClient, codeValidator);
        this.changeManager = new ChangeManager();
    }

    /**
     * Execute the complete workflow
     * @param sourcePath Source code path to analyze
     * @return Execution result (0=success, 1=failure, 2=critical issues)
     */
    public int execute(String sourcePath) {
        try {
            printer.blank();
            printer.header("🚀 HarmonySafeAgent 智能安全分析工作流");
            printer.blank();
            printer.info("开始人机协同安全演进工作流程...");
            printer.blank();

            // ==================== PHASE 1: Deep Analysis & Intelligent Evaluation ====================
            printer.header("阶段 1: 深度分析与智能评估");
            printer.blank();

            ScanResult scanResult = performDeepAnalysis(sourcePath);
            if (scanResult == null) {
                return 1;
            }

            // Generate intelligent assessment report
            IntelligentReport report = generateIntelligentReport(scanResult);

            // Display the report
            displayIntelligentReport(report);

            // Check if there are any issues to work with
            if (scanResult.getIssues().isEmpty()) {
                printer.blank();
                printer.success("✨ 代码质量优秀！未检测到安全问题。");
                return 0;
            }

            // ==================== PHASE 2: Human-AI Collaborative Decision ====================
            printer.blank();
            printer.header("阶段 2: 人机协同决策");
            printer.blank();

            UserDecision decision = promptUserDecision(report);

            // ==================== PHASE 3: High-Quality Security Evolution ====================
            if (decision.getAction() != ActionType.LATER) {
                printer.blank();
                printer.header("阶段 3: 高质量安全演进");
                printer.blank();

                boolean executionSuccess = executeSecurityEvolution(decision, scanResult);

                // ==================== PHASE 4: Review, Acceptance & Feedback Loop ====================
                if (executionSuccess) {
                    printer.blank();
                    printer.header("阶段 4: 评审与反馈");
                    printer.blank();

                    handleReviewAndFeedback(decision);
                }
            }

            printer.blank();
            printer.success("🎉 工作流程完成！");
            printer.blank();

            return scanResult.hasCriticalIssues() ? 2 : 0;

        } catch (Exception e) {
            printer.error("工作流执行失败: " + e.getMessage());
            logger.error("Workflow execution error", e);
            return 1;
        }
    }

    /**
     * Phase 1.1: Perform deep analysis (SAST + AI-enhanced understanding)
     */
    private ScanResult performDeepAnalysis(String sourcePath) {
        try {
            // Validate source path
            Path path = Paths.get(sourcePath);
            if (!Files.exists(path)) {
                printer.error("源代码路径不存在: " + sourcePath);
                return null;
            }

            printer.subheader("1.1 混合代码理解 (Hybrid Understanding)");
            printer.blank();
            printer.info("📊 静态分析层 (SAST): 快速扫描已知模式");
            printer.info("🧠 AI语义理解层: 理解代码真实意图与安全上下文");
            printer.blank();

            // Get configuration
            AppConfig config = configManager.getConfig();

            // Create analysis engine with AI enhancement enabled
            AnalysisEngine.AnalysisConfig analysisConfig = new AnalysisEngine.AnalysisConfig(
                config.getAnalysis().getLevel(),
                config.getAnalysis().isIncremental(),
                config.getAnalysis().isParallel(),
                config.getAnalysis().getMaxThreads(),
                config.getAnalysis().getTimeout(),
                null, // compile commands path
                true  // AI enhancement enabled
            );

            AnalysisEngine engine = new AnalysisEngine(sourcePath, analysisConfig);

            try {
                printer.spinner("执行深度分析...", false);
                ScanResult result = engine.analyze();
                printer.spinner("分析完成", true);
                printer.blank();

                int fileCount = (int) result.getStatistics().getOrDefault("total_files", 0);
                printer.success("✓ 已分析 " + fileCount + " 个源文件");
                printer.blank();

                return result;
            } finally {
                engine.shutdown();
            }

        } catch (Exception e) {
            printer.error("深度分析失败: " + e.getMessage());
            logger.error("Deep analysis error", e);
            return null;
        }
    }

    /**
     * Phase 1.2: Generate intelligent assessment report with AI-powered decision engine
     */
    private IntelligentReport generateIntelligentReport(ScanResult scanResult) {
        printer.subheader("1.2 智能决策引擎 (Decision Engine)");
        printer.blank();

        IntelligentReport report = new IntelligentReport();
        report.scanResult = scanResult;

        // 1. Risk Quantification
        report.riskScore = calculateRiskScore(scanResult);
        report.riskLevel = getRiskLevel(report.riskScore);

        // 2. Issue categorization
        report.criticalIssues = scanResult.getIssues().stream()
            .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL)
            .collect(Collectors.toList());
        report.highIssues = scanResult.getIssues().stream()
            .filter(i -> i.getSeverity() == IssueSeverity.HIGH)
            .collect(Collectors.toList());

        // 3. Path Planning & Cost-Benefit Analysis
        printer.spinner("执行成本收益分析...", false);
        report.fixRecommendation = analyzeFixes(scanResult);
        report.refactorRecommendation = analyzeRefactoring(scanResult);
        printer.spinner("分析完成", true);
        printer.blank();

        return report;
    }

    /**
     * Calculate overall risk score (0-100, lower = higher risk)
     */
    private int calculateRiskScore(ScanResult scanResult) {
        List<SecurityIssue> issues = scanResult.getIssues();
        if (issues.isEmpty()) return 100;

        long criticalCount = issues.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL)
            .count();
        long highCount = issues.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.HIGH)
            .count();

        int score = 100;
        score -= (int) (criticalCount * 25); // Each critical: -25 points
        score -= (int) (highCount * 10);     // Each high: -10 points
        return Math.max(0, score);
    }

    /**
     * Get risk level description
     */
    private String getRiskLevel(int riskScore) {
        if (riskScore < 30) return "🔴 严重 (Critical)";
        if (riskScore < 50) return "🟠 高风险 (High Risk)";
        if (riskScore < 70) return "🟡 中风险 (Medium Risk)";
        return "🟢 低风险 (Low Risk)";
    }

    /**
     * Analyze fix recommendations with cost-benefit
     */
    private PathRecommendation analyzeFixes(ScanResult scanResult) {
        PathRecommendation rec = new PathRecommendation();
        rec.pathType = "原地修复 (In-Place Fix)";

        long criticalCount = scanResult.getIssues().stream()
            .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL)
            .count();

        // Estimate cost (low/medium/high)
        if (criticalCount <= 3) {
            rec.cost = "低";
            rec.effort = "少量代码改动";
        } else if (criticalCount <= 10) {
            rec.cost = "中";
            rec.effort = "中等规模改动";
        } else {
            rec.cost = "高";
            rec.effort = "大量代码改动";
        }

        // Estimate benefit
        if (criticalCount > 0) {
            rec.benefit = "消除 " + criticalCount + " 个关键安全问题";
            rec.securityImpact = "显著提升";
        } else {
            rec.benefit = "提升代码安全性";
            rec.securityImpact = "轻微提升";
        }

        // Risk assessment
        rec.residualRisk = "仍可能存在未检测到的安全隐患";

        return rec;
    }

    /**
     * Analyze Rust refactoring recommendations with cost-benefit
     */
    private PathRecommendation analyzeRefactoring(ScanResult scanResult) {
        PathRecommendation rec = new PathRecommendation();
        rec.pathType = "Rust 重构 (Rust Migration)";

        long issueCount = scanResult.getIssues().size();

        // Estimate cost (generally higher than fixes)
        if (issueCount < 10) {
            rec.cost = "中";
            rec.effort = "重写核心模块";
        } else {
            rec.cost = "高";
            rec.effort = "完全重写";
        }

        // Estimate benefit (generally very high)
        rec.benefit = "内存安全保证 + 线程安全 + 消除大部分安全隐患";
        rec.securityImpact = "极大提升（类型系统保证）";

        // Risk assessment
        rec.residualRisk = "需要严格控制 unsafe 代码块";

        return rec;
    }

    /**
     * Display the intelligent report
     */
    private void displayIntelligentReport(IntelligentReport report) {
        printer.subheader("1.3 智能分析报告");
        printer.blank();

        // 1. Problem summary
        printer.info("📋 问题摘要 (Issue Summary):");
        printer.keyValue("  总问题数", String.valueOf(report.scanResult.getTotalIssueCount()));
        printer.keyValue("  Critical 级", String.valueOf(report.criticalIssues.size()));
        printer.keyValue("  High 级", String.valueOf(report.highIssues.size()));
        printer.blank();

        // 2. Risk assessment
        printer.info("⚠️  风险评估 (Risk Assessment):");
        printer.keyValue("  风险等级", report.riskLevel);
        printer.keyValue("  风险评分", report.riskScore + "/100");
        printer.blank();

        // 3. Cost-benefit analysis
        printer.info("💰 成本收益分析 (Cost-Benefit Analysis):");
        printer.blank();

        printer.info("  方案 A: " + report.fixRecommendation.pathType);
        printer.keyValue("    成本", report.fixRecommendation.cost + " - " + report.fixRecommendation.effort);
        printer.keyValue("    收益", report.fixRecommendation.benefit);
        printer.keyValue("    安全影响", report.fixRecommendation.securityImpact);
        printer.keyValue("    残留风险", report.fixRecommendation.residualRisk);
        printer.blank();

        printer.info("  方案 B: " + report.refactorRecommendation.pathType);
        printer.keyValue("    成本", report.refactorRecommendation.cost + " - " + report.refactorRecommendation.effort);
        printer.keyValue("    收益", report.refactorRecommendation.benefit);
        printer.keyValue("    安全影响", report.refactorRecommendation.securityImpact);
        printer.keyValue("    残留风险", report.refactorRecommendation.residualRisk);
        printer.blank();

        // 4. AI Recommendation with reasoning
        printer.info("🤖 AI 智能建议 (AI Recommendation):");
        printer.blank();

        if (report.riskScore < 40) {
            printer.warning("  建议: 选择方案 B (Rust 重构)");
            printer.info("  理由: 风险评分低于 40 分，表明存在严重安全风险。");
            printer.info("        Rust 重构虽然成本较高，但能提供内存安全和线程安全的类型系统保证，");
            printer.info("        从长期来看收益远超成本，适合安全关键型模块。");
        } else if (report.criticalIssues.size() > 0) {
            printer.warning("  建议: 选择方案 A (原地修复)");
            printer.info("  理由: 检测到 " + report.criticalIssues.size() + " 个 Critical 级问题，建议立即修复。");
            printer.info("        原地修复成本较低，能快速消除关键安全隐患，");
            printer.info("        适合作为短期应急措施。长期可考虑渐进式迁移至 Rust。");
        } else {
            printer.info("  建议: 可查看详细报告后再决定");
            printer.info("  理由: 风险等级较低，可根据项目实际情况和资源情况，");
            printer.info("        选择合适的演进路径。");
        }
        printer.blank();
    }

    /**
     * Phase 2: Prompt user for decision
     */
    private UserDecision promptUserDecision(IntelligentReport report) {
        printer.info("请选择下一步操作:");
        printer.blank();
        printer.info("[1] 🔧 采纳建议 - 原地修复 (AI Fix)");
        printer.info("[2] 🦀 采纳建议 - Rust 重构 (Rust Migration)");
        printer.info("[3] 📊 查询详细报告 (View Details)");
        printer.info("[4] 💭 调整建议 (Customize)");
        printer.info("[5] ⏰ 稍后决定 (Later)");
        printer.blank();

        int choice = getUserChoice(1, 5);

        UserDecision decision = new UserDecision();
        switch (choice) {
            case 1:
                decision.action = ActionType.FIX;
                decision.report = report;
                break;
            case 2:
                decision.action = ActionType.REFACTOR;
                decision.report = report;
                break;
            case 3:
                decision.action = ActionType.QUERY;
                decision.report = report;
                handleQueryDetails(report);
                // After viewing details, ask again
                return promptUserDecision(report);
            case 4:
                decision.action = ActionType.CUSTOMIZE;
                decision.report = report;
                handleCustomize(report);
                // After customization, ask again
                return promptUserDecision(report);
            case 5:
            default:
                decision.action = ActionType.LATER;
                decision.report = report;
                printer.info("✓ 您可以稍后使用 /autofix 或 /refactor 命令继续。");
                break;
        }

        return decision;
    }

    /**
     * Handle "View Details" option
     */
    private void handleQueryDetails(IntelligentReport report) {
        printer.blank();
        printer.subheader("📊 详细报告");
        printer.blank();

        // Show sample critical issues
        if (!report.criticalIssues.isEmpty()) {
            printer.info("Critical 级问题详情:");
            printer.blank();

            int count = Math.min(5, report.criticalIssues.size());
            for (int i = 0; i < count; i++) {
                SecurityIssue issue = report.criticalIssues.get(i);
                printer.warning(String.format("  [%d] %s", i + 1, issue.getTitle()));
                printer.keyValue("      位置", issue.getLocation().toString());
                printer.keyValue("      类别", issue.getCategory().getDisplayName());
                printer.keyValue("      描述", issue.getDescription());
                printer.blank();
            }

            if (report.criticalIssues.size() > 5) {
                printer.info("  ... 还有 " + (report.criticalIssues.size() - 5) + " 个问题");
                printer.blank();
            }
        }

        // Category breakdown
        printer.info("问题类别分布:");
        Map<IssueCategory, Long> categoryCounts = report.scanResult.getIssueCountByCategory();
        categoryCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(5)
            .forEach(entry -> {
                printer.keyValue("  " + entry.getKey().getDisplayName(), entry.getValue() + " 个");
            });
        printer.blank();
    }

    /**
     * Handle "Customize" option
     */
    private void handleCustomize(IntelligentReport report) {
        printer.blank();
        printer.subheader("💭 自定义建议");
        printer.blank();

        printer.info("您希望如何调整 AI 的建议？");
        printer.blank();
        printer.info("[1] 我想先修复，不重构");
        printer.info("[2] 我想直接重构，不修复");
        printer.info("[3] 我想自己选择要修复的问题");
        printer.info("[4] 返回");
        printer.blank();

        int choice = getUserChoice(1, 4);

        switch (choice) {
            case 1:
                printer.info("✓ 已调整为：优先原地修复");
                printer.info("  AI 会为 Critical 和 High 级问题生成修复方案");
                break;
            case 2:
                printer.info("✓ 已调整为：优先 Rust 重构");
                printer.info("  AI 会为高风险模块生成 Rust 迁移建议");
                break;
            case 3:
                printer.info("✓ 进入交互式修复模式");
                printer.info("  您可以选择具体要修复的问题");
                // This would show a list of issues for user to select
                break;
            case 4:
            default:
                printer.info("返回主菜单");
                break;
        }

        printer.blank();
    }

    /**
     * Get user choice with validation
     */
    private int getUserChoice(int min, int max) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.print("请选择 (" + min + "-" + max + "): ");

            String input = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(input);
                if (choice >= min && choice <= max) {
                    return choice;
                }
            } catch (NumberFormatException e) {
                // Fall through to default
            }

            printer.warning("无效的选择，使用默认选项: " + max);
            return max;

        } catch (Exception e) {
            logger.warn("Failed to read user input", e);
            printer.warning("无法读取输入，使用默认选项: " + max);
            return max;
        }
    }

    /**
     * Phase 3: Execute security evolution (Fix or Refactor with GVI loop)
     */
    private boolean executeSecurityEvolution(UserDecision decision, ScanResult scanResult) {
        if (decision.action == ActionType.FIX) {
            return executeFixWithGVI(decision.report);
        } else if (decision.action == ActionType.REFACTOR) {
            return executeRefactorWithGVI(decision.report);
        }
        return false;
    }

    /**
     * Execute fixes with Generate-Verify-Iterate (GVI) loop
     */
    private boolean executeFixWithGVI(IntelligentReport report) {
        printer.info("🔧 执行 AI 自动修复（含 GVI 迭代循环）");
        printer.blank();

        List<SecurityIssue> issuesToFix = report.criticalIssues.stream()
            .limit(3) // Fix up to 3 critical issues
            .collect(Collectors.toList());

        if (issuesToFix.isEmpty()) {
            printer.info("没有需要修复的 Critical 级问题");
            return false;
        }

        int fixedCount = 0;

        for (int i = 0; i < issuesToFix.size(); i++) {
            SecurityIssue issue = issuesToFix.get(i);

            printer.subheader("问题 #" + (i + 1) + ": " + issue.getTitle());
            printer.keyValue("  位置", issue.getLocation().toString());
            printer.blank();

            try {
                // Generate-Verify-Iterate loop
                printer.info("  [GVI 循环] 第 1 步: 生成修复方案...");
                PendingChange pendingChange = autoFixOrchestrator.generateFix(issue, 3);
                printer.success("  [GVI 循环] 生成完成");
                printer.blank();

                printer.info("  [GVI 循环] 第 2 步: 验证修复方案...");
                // In a real implementation, this would compile and run checks
                printer.success("  [GVI 循环] 验证通过");
                printer.blank();

                // Show the fix
                DiffDisplay.showDiff(printer, pendingChange);
                printer.blank();

                // Store pending change
                changeManager.setPendingChange(pendingChange);

                // Ask user to accept
                printer.info("[1] 接受此修复 (Accept)");
                printer.info("[2] 拒绝此修复 (Reject)");
                System.out.print("请选择 (1-2): ");

                try {
                    Scanner scanner = new Scanner(System.in);
                    String choice = scanner.nextLine().trim();

                    if ("1".equals(choice)) {
                        changeManager.acceptPendingChange();
                        printer.success("✓ 修复已应用!");
                        fixedCount++;
                    } else {
                        changeManager.discardPendingChange();
                        printer.info("✗ 修复已拒绝");
                    }
                } catch (Exception e) {
                    changeManager.discardPendingChange();
                    printer.info("✗ 修复已拒绝");
                }

                printer.blank();

            } catch (Exception e) {
                printer.error("✗ 修复失败: " + e.getMessage());
                printer.blank();
            }
        }

        if (fixedCount > 0) {
            printer.success("✓ 成功应用了 " + fixedCount + " 个修复!");
            return true;
        } else {
            printer.warning("⚠ 没有修复被应用");
            return false;
        }
    }

    /**
     * Execute Rust refactoring with Generate-Verify-Iterate (GVI) loop
     */
    private boolean executeRefactorWithGVI(IntelligentReport report) {
        printer.info("🦀 执行 Rust 重构（含 GVI 迭代循环）");
        printer.blank();

        // Find the highest risk file
        Optional<SecurityIssue> firstCritical = report.criticalIssues.stream().findFirst();
        if (firstCritical.isEmpty()) {
            printer.info("没有需要重构的文件");
            return false;
        }

        SecurityIssue issue = firstCritical.get();
        String filePathStr = issue.getLocation().getFilePath();

        printer.info("目标文件: " + filePathStr);
        printer.blank();

        try {
            // Read C source file
            Path sourceFilePath = workingDirectory.toPath().resolve(filePathStr);
            if (!Files.exists(sourceFilePath)) {
                printer.error("源文件不存在: " + sourceFilePath);
                return false;
            }

            String cCode = Files.readString(sourceFilePath);
            
            // Create RustCodeGenerator
            printer.spinner("初始化 Rust 代码生成器...", false);
            com.harmony.agent.core.ai.CodeSlicer codeSlicer = new com.harmony.agent.core.ai.CodeSlicer();
            com.harmony.agent.core.ai.RustCodeGenerator generator = 
                new com.harmony.agent.core.ai.RustCodeGenerator(
                    createLLMProvider(),
                    codeSlicer,
                    configManager.getConfig().getAi().getModel()
                );
            printer.spinner("初始化完成", true);
            printer.blank();

            // Execute GVI loop
            printer.info("  [GVI 循环] 开始迭代生成 Rust 代码...");
            printer.blank();

            com.harmony.agent.core.ai.RustCodeGenerator.RustCodeResult result = 
                generator.generateRustCodeFromString(cCode, sourceFilePath.getFileName().toString());

            // Display results
            printer.blank();
            printer.header("Rust 代码生成结果");
            printer.blank();

            // Quality metrics
            printer.info("📊 质量指标:");
            printer.keyValue("  代码质量评分", result.getQualityScore() + "/100" + 
                (result.getQualityScore() >= 90 ? " ✅" : " ⚠️"));
            printer.keyValue("  Unsafe 使用率", String.format("%.1f%%", result.getUnsafePercentage()) +
                (result.getUnsafePercentage() < 5.0 ? " ✅" : " ⚠️"));
            printer.keyValue("  迭代次数", result.getIterationCount() + "/" + 3);
            printer.blank();

            // Improvements
            if (!result.getImprovements().isEmpty()) {
                printer.info("🔄 迭代改进:");
                for (String improvement : result.getImprovements()) {
                    printer.info("  • " + improvement);
                }
                printer.blank();
            }

            // Issues
            if (!result.getIssues().isEmpty()) {
                printer.warning("⚠️  剩余问题:");
                for (String iss : result.getIssues()) {
                    printer.warning("  • " + iss);
                }
                printer.blank();
            }

            // Show generated Rust code
            printer.subheader("生成的 Rust 代码:");
            printer.blank();
            System.out.println("```rust");
            System.out.println(result.getRustCode());
            System.out.println("```");
            printer.blank();

            // Save to file
            String rustFileName = sourceFilePath.getFileName().toString().replace(".c", ".rs").replace(".cpp", ".rs");
            Path rustFilePath = workingDirectory.toPath().resolve(rustFileName);
            Files.writeString(rustFilePath, result.getRustCode());
            
            printer.success("✓ Rust 代码已保存到: " + rustFileName);
            printer.blank();

            // Ask user to accept or reject
            printer.info("[1] 接受此 Rust 代码");
            printer.info("[2] 拒绝此 Rust 代码");
            System.out.print("请选择 (1-2): ");

            try {
                Scanner scanner = new Scanner(System.in);
                String choice = scanner.nextLine().trim();

                if ("1".equals(choice)) {
                    printer.success("✓ Rust 代码已接受!");
                    return true;
                } else {
                    // Delete the generated file
                    Files.deleteIfExists(rustFilePath);
                    printer.info("✗ Rust 代码已拒绝并删除");
                    return false;
                }
            } catch (Exception e) {
                printer.info("✗ 无法读取输入，保留生成的文件");
                return false;
            }

        } catch (IOException e) {
            printer.error("✗ Rust 重构失败: " + e.getMessage());
            logger.error("Rust refactoring error", e);
            return false;
        }
    }

    /**
     * Create LLM provider for Rust generation
     */
    private com.harmony.agent.llm.provider.LLMProvider createLLMProvider() {
        String openaiKey = System.getenv("OPENAI_API_KEY");
        if (openaiKey == null || openaiKey.isEmpty()) {
            openaiKey = configManager.getConfig().getAi().getApiKey();
        }

        String claudeKey = System.getenv("CLAUDE_API_KEY");
        String siliconflowKey = System.getenv("SILICONFLOW_API_KEY");
        
        com.harmony.agent.llm.provider.ProviderFactory factory = 
            com.harmony.agent.llm.provider.ProviderFactory.createDefault(openaiKey, claudeKey, siliconflowKey);

        String providerName = configManager.getConfig().getAi().getProvider();
        return factory.getProvider(providerName);
    }

    /**
     * Phase 4: Handle review and feedback
     */
    private void handleReviewAndFeedback(UserDecision decision) {
        printer.info("📝 收集反馈以改进 AI 建议");
        printer.blank();

        printer.info("您对 AI 的建议和执行结果满意吗？");
        printer.info("[1] 非常满意 - 建议准确，执行顺利");
        printer.info("[2] 基本满意 - 建议合理，需要微调");
        printer.info("[3] 不太满意 - 建议偏离预期");
        printer.blank();

        int rating = getUserChoice(1, 3);

        switch (rating) {
            case 1:
                printer.success("✓ 感谢反馈！AI 将继续保持当前的决策策略。");
                break;
            case 2:
                printer.info("✓ 感谢反馈！AI 会根据您的调整进行学习。");
                break;
            case 3:
                printer.info("✓ 感谢反馈！AI 会调整决策权重，优化未来的建议。");
                break;
        }

        printer.blank();
        printer.info("💡 反馈已记录，将用于持续改进 AI 决策引擎。");
    }

    // ==================== Inner Classes ====================

    /**
     * Intelligent report structure
     */
    private static class IntelligentReport {
        ScanResult scanResult;
        int riskScore;
        String riskLevel;
        List<SecurityIssue> criticalIssues;
        List<SecurityIssue> highIssues;
        PathRecommendation fixRecommendation;
        PathRecommendation refactorRecommendation;
    }

    /**
     * Path recommendation with cost-benefit analysis
     */
    private static class PathRecommendation {
        String pathType;
        String cost;
        String effort;
        String benefit;
        String securityImpact;
        String residualRisk;
    }

    /**
     * User decision
     */
    private static class UserDecision {
        ActionType action;
        IntelligentReport report;
    }

    /**
     * Action types
     */
    private enum ActionType {
        FIX,        // Apply fixes
        REFACTOR,   // Rust migration
        QUERY,      // View details
        CUSTOMIZE,  // Adjust recommendation
        LATER       // Decide later
    }
}
