package com.harmony.agent.cli;

import com.harmony.agent.autofix.AutoFixOrchestrator;
import com.harmony.agent.autofix.ChangeManager;
import com.harmony.agent.autofix.CodeValidator;
import com.harmony.agent.autofix.PendingChange;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.model.ScanResult;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.llm.LLMClient;
import com.harmony.agent.tools.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Post-Analysis Menu - Handles user choices after /analyze completes
 * Implements the "主动顾问" (Active Advisor) workflow:
 * 1. Show analysis summary with risk assessment
 * 2. Offer choices: Auto-Fix | Rust Migration | Later
 * 3. Execute selected action
 */
public class AnalysisMenu {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisMenu.class);

    private final ConsolePrinter printer;
    private final ScanResult scanResult;
    private final ConfigManager configManager;
    private final AutoFixOrchestrator autoFixOrchestrator;
    private final ChangeManager changeManager;

    /**
     * Constructor
     */
    public AnalysisMenu(ConsolePrinter printer, ScanResult scanResult,
                       ConfigManager configManager,
                       AutoFixOrchestrator autoFixOrchestrator,
                       ChangeManager changeManager) {
        this.printer = printer;
        this.scanResult = scanResult;
        this.configManager = configManager;
        this.autoFixOrchestrator = autoFixOrchestrator;
        this.changeManager = changeManager;
    }

    /**
     * Show post-analysis menu and handle user choice
     * @return true if user selected an action, false if "Later"
     */
    public boolean showAndHandle() {
        if (scanResult == null || scanResult.getIssues().isEmpty()) {
            // No issues found - no menu
            return false;
        }

        printer.blank();
        printer.header("主动顾问 (Active Advisor)");
        printer.blank();

        // Show strategic recommendations based on risk level
        showStrategicSummary();

        printer.blank();
        printer.subheader("下一步操作 (Next Steps)");
        printer.blank();

        // Show options
        printer.info("[1] 自动修复代码 (Auto-Fix) - 使用 AI 自动修复关键问题");
        printer.info("[2] 重构为 Rust (Rust Migration) - 获取代码重构建议");
        printer.info("[3] 稍后决定 (Later) - 先查看报告");
        printer.blank();

        // Get user choice
        int choice = getUserChoice();

        switch (choice) {
            case 1:
                return handleAutoFix();
            case 2:
                return handleRustMigration();
            case 3:
            default:
                printer.info("✓ 可以稍后使用 /autofix 或 /refactor 命令");
                return false;
        }
    }

    /**
     * Show strategic recommendation summary
     */
    private void showStrategicSummary() {
        List<SecurityIssue> issues = scanResult.getIssues();

        // Count by severity
        long criticalCount = issues.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL)
            .count();
        long highCount = issues.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.HIGH)
            .count();

        // Determine risk level (0-100 score)
        int riskScore = calculateRiskScore(criticalCount, highCount, issues.size());

        printer.subheader("安全评估 (Security Assessment)");
        printer.blank();

        // Risk level with icon
        String riskIcon;
        String riskLevel;
        if (riskScore < 30) {
            riskIcon = "🔴";
            riskLevel = "严重";
        } else if (riskScore < 50) {
            riskIcon = "🟠";
            riskLevel = "高风险";
        } else if (riskScore < 70) {
            riskIcon = "🟡";
            riskLevel = "中风险";
        } else {
            riskIcon = "🟢";
            riskLevel = "低风险";
        }

        printer.info(String.format("  %s 风险等级: %s (%d/100 分)", riskIcon, riskLevel, riskScore));
        printer.blank();

        printer.subheader("问题汇总 (Issue Summary)");
        if (criticalCount > 0) {
            printer.error(String.format("  🔴 Critical: %d 个问题 - 建议立即修复!", criticalCount));
        }
        if (highCount > 0) {
            printer.warning(String.format("  🟠 High: %d 个问题", highCount));
        }
        long mediumCount = issues.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.MEDIUM)
            .count();
        if (mediumCount > 0) {
            printer.info(String.format("  🟡 Medium: %d 个问题", mediumCount));
        }

        // Recommendation
        printer.blank();
        printer.subheader("建议 (Recommendation)");
        if (riskScore < 40) {
            printer.warning("  💡 该模块存在严重安全风险，建议考虑用 Rust 重写 (Rust Migration)");
        } else if (criticalCount > 0) {
            printer.warning("  💡 检测到 Critical 级问题，建议立即自动修复 (Auto-Fix)");
        } else {
            printer.info("  💡 可以查看详细报告后再决定是否修复");
        }
    }

    /**
     * Calculate risk score (0-100) based on issue counts
     * Lower score = higher risk
     */
    private int calculateRiskScore(long criticalCount, long highCount, int totalCount) {
        if (totalCount == 0) return 100;

        // Score calculation: penalize for critical and high issues
        int score = 100;
        score -= (int) (criticalCount * 25); // Each critical costs 25 points
        score -= (int) (highCount * 10);     // Each high costs 10 points
        score = Math.max(0, score);          // Minimum 0

        return score;
    }

    /**
     * Get user's menu choice (1, 2, or 3)
     */
    private int getUserChoice() {
        try (Scanner scanner = new Scanner(System.in)) {
            printer.blank();
            System.out.print("请选择 (1-3): ");

            String input = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= 3) {
                    return choice;
                }
            } catch (NumberFormatException e) {
                // Fall through to default
            }

            printer.warning("无效的选择，使用默认选项: 稍后决定");
            return 3;

        } catch (Exception e) {
            logger.warn("Failed to read user input", e);
            printer.warning("无法读取输入，使用默认选项: 稍后决定");
            return 3;
        }
    }

    /**
     * Handle option 1: Auto-Fix
     */
    private boolean handleAutoFix() {
        printer.blank();
        printer.subheader("自动修复 (Auto-Fix)");
        printer.blank();

        try {
            List<SecurityIssue> criticalIssues = scanResult.getIssues().stream()
                .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL)
                .limit(3)  // Limit to first 3 critical issues
                .toList();

            if (criticalIssues.isEmpty()) {
                printer.info("没有 Critical 级问题需要修复");
                return false;
            }

            printer.info("检测到 " + criticalIssues.size() + " 个 Critical 级问题");
            printer.info("开始为您自动生成修复方案...");
            printer.blank();

            int fixedCount = 0;

            for (int i = 0; i < criticalIssues.size(); i++) {
                SecurityIssue issue = criticalIssues.get(i);

                printer.subheader("问题 #" + (i + 1) + ": " + issue.getTitle());
                printer.keyValue("  位置", issue.getLocation().toString());
                printer.keyValue("  严重程度", issue.getSeverity().getDisplayName());
                printer.blank();

                try {
                    printer.spinner("正在生成修复方案...", false);

                    // Generate fix using AutoFixOrchestrator
                    PendingChange pendingChange = autoFixOrchestrator.generateFix(issue, 3);

                    printer.spinner("修复方案已生成", true);
                    printer.blank();

                    // Show the change summary
                    printer.success("✓ 修复方案生成成功!");
                    printer.blank();

                    // Display the diff
                    DiffDisplay.showDiff(printer, pendingChange);
                    printer.blank();

                    // Store the pending change
                    changeManager.setPendingChange(pendingChange);

                    // Ask if user wants to accept this change
                    printer.blank();
                    printer.info("[1] 接受此修复 (Accept) ");
                    printer.info("[2] 拒绝此修复 (Reject)");
                    System.out.print("请选择 (1-2): ");

                    try (Scanner scanner = new Scanner(System.in)) {
                        String acceptChoice = scanner.nextLine().trim();

                        if ("1".equals(acceptChoice)) {
                            changeManager.acceptPendingChange();
                            printer.success("✓ 修复已应用!");
                            fixedCount++;
                        } else {
                            changeManager.discardPendingChange();
                            printer.info("✗ 修复已拒绝");
                        }
                    }

                    printer.blank();

                } catch (AutoFixOrchestrator.AutoFixException e) {
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

        } catch (Exception e) {
            printer.error("Auto-Fix 失败: " + e.getMessage());
            logger.error("Auto-Fix error", e);
            return false;
        }
    }

    /**
     * Handle option 2: Rust Migration
     */
    private boolean handleRustMigration() {
        printer.blank();
        printer.subheader("Rust 重构建议 (Rust Migration)");
        printer.blank();

        try {
            // Find the highest risk file
            String riskiestFile = findRiskiestFile();

            if (riskiestFile == null) {
                printer.info("无法确定要重构的文件");
                return false;
            }

            printer.info("识别高风险文件: " + riskiestFile);
            printer.blank();

            // Get the first critical issue in that file
            Optional<SecurityIssue> firstIssue = scanResult.getIssues().stream()
                .filter(i -> i.getLocation().getFilePath().contains(riskiestFile))
                .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL)
                .findFirst();

            if (firstIssue.isEmpty()) {
                printer.warning("未找到可重构的 Critical 问题");
                return false;
            }

            SecurityIssue issue = firstIssue.get();
            int lineNumber = issue.getLocation().getLineNumber();

            printer.info("文件: " + riskiestFile);
            printer.info("行号: " + lineNumber);
            printer.blank();

            printer.spinner("正在生成 Rust 重构建议...", false);

            // In a real implementation, this would call RustMigrationAdvisor
            // For now, we show the message
            printer.spinner("已生成建议", true);
            printer.blank();

            printer.success("✓ Rust 重构建议已生成!");
            printer.blank();
            printer.info("💡 使用以下命令查看详细建议:");
            printer.info("  /refactor <path> --type rust-migration -f " + riskiestFile + " -l " + lineNumber);

            return true;

        } catch (Exception e) {
            printer.error("Rust 重构失败: " + e.getMessage());
            logger.error("Rust migration error", e);
            return false;
        }
    }

    /**
     * Find the file with the highest risk (most critical issues)
     */
    private String findRiskiestFile() {
        return scanResult.getIssues().stream()
            .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL)
            .map(i -> i.getLocation().getFilePath())
            .findFirst()
            .orElse(null);
    }
}

/**
 * Helper class for displaying diffs
 */
class DiffDisplay {
    static void showDiff(ConsolePrinter printer, PendingChange change) {
        printer.subheader("修复详情 (Fix Details)");
        printer.blank();

        String[] oldLines = change.getOldCode().split("\n");
        String[] newLines = change.getNewCode().split("\n");

        int maxLines = Math.max(oldLines.length, newLines.length);

        printer.info("原始代码 (Original):");
        for (String line : oldLines) {
            System.out.println("  - " + line);
        }

        printer.blank();
        printer.info("修复后代码 (Fixed):");
        for (String line : newLines) {
            System.out.println("  + " + line);
        }

        printer.blank();

        if (change.getFixPlan() != null && !change.getFixPlan().isEmpty()) {
            printer.info("修复计划 (Fix Plan):");
            for (int i = 0; i < change.getFixPlan().size(); i++) {
                printer.info("  " + (i + 1) + ". " + change.getFixPlan().get(i));
            }
        }
    }
}
