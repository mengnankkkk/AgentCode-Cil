package com.harmony.agent.cli;

import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.strategic.SecurityScoringService;
import com.harmony.agent.strategic.TriageAdvisor;
import com.harmony.agent.strategic.SecurityScoringService.SecurityScore;
import com.harmony.agent.strategic.TriageAdvisor.TriageRecommendation;
import com.harmony.agent.strategic.TriageAdvisor.StrategicSummary;
import com.harmony.agent.tools.ToolExecutor;
import com.harmony.agent.tools.result.AnalysisResult;
import com.harmony.agent.autofix.AutoFixOrchestrator;
import com.harmony.agent.autofix.ChangeManager;
import com.harmony.agent.autofix.CodeValidator;
import com.harmony.agent.llm.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * Strategic Analysis Command - 实现完整的 P-Strategic 工作流
 * 包括 T1.1 SecurityScoringService 和 T1.2 TriageAdvisor
 */
@Command(
    name = "strategic-analyze",
    aliases = {"sanalyze", "sa"},
    description = "Run strategic security analysis with scoring and triage recommendations",
    mixinStandardHelpOptions = true
)
public class StrategicAnalysisCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(StrategicAnalysisCommand.class);

    @ParentCommand
    private HarmonyAgentCLI parent;

    @Parameters(
        index = "0",
        description = "Source code path to analyze"
    )
    private String sourcePath;

    private SecurityScoringService scoringService;
    private TriageAdvisor triageAdvisor;
    private ToolExecutor toolExecutor;
    private ConsolePrinter printer;

    @Override
    public Integer call() {
        printer = parent.getPrinter();
        ConfigManager configManager = parent.getConfigManager();

        try {
            // 验证源码路径
            Path path = Paths.get(sourcePath);
            if (!Files.exists(path)) {
                printer.error("Source path does not exist: " + sourcePath);
                return 1;
            }

            // 初始化组件
            scoringService = new SecurityScoringService();
            triageAdvisor = new TriageAdvisor();
            toolExecutor = new ToolExecutor(new File(System.getProperty("user.dir")));

            // 显示分析开始
            printer.blank();
            printer.header("🛡️ Strategic Security Analysis");
            printer.info("Target: " + sourcePath);
            printer.info("Mode: P-Strategic (T1.1 Scoring + T1.2 Triage)");
            printer.blank();

            // 第一步：运行静态分析
            printer.spinner("Running static analysis...", false);
            AnalysisResult analysisResult = toolExecutor.analyzeWithSpotBugs(null);
            printer.spinner("Running static analysis", true);

            // 第二步：发现源码文件
            List<String> sourceFiles = discoverSourceFiles(path);
            printer.info("Discovered " + sourceFiles.size() + " source files");
            printer.blank();

            // 第三步：运行 P-Strategic (T1.1 + T1.2)
            printer.header("P-Strategic Analysis");
            printer.blank();

            List<SecurityScore> scores = new ArrayList<>();
            List<TriageRecommendation> recommendations = new ArrayList<>();

            for (String filePath : sourceFiles) {
                // T1.1 SecurityScoringService 计算评分
                printer.spinner("Scoring " + new File(filePath).getName() + "...", false);
                SecurityScore score = scoringService.calculateScore(filePath, analysisResult);
                scores.add(score);
                printer.spinner("Scoring " + new File(filePath).getName(), true);

                // T1.2 TriageAdvisor 决策
                TriageRecommendation recommendation = triageAdvisor.recommend(score);
                recommendations.add(recommendation);

                // 显示结果
                displayModuleResult(score, recommendation);
            }

            // 第四步：生成战略摘要
            printer.blank();
            printer.header("Strategic Summary");
            StrategicSummary summary = triageAdvisor.generateSummary(recommendations);
            printer.info(summary.toString());
            printer.blank();

            // 第五步：显示详细建议
            displayDetailedRecommendations(recommendations);

            // 第六步：Agent 主动回应 - 提供交互选项
            showAgentResponse(recommendations);

            return 0;

        } catch (Exception e) {
            printer.error("Strategic analysis failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /**
     * 发现源码文件
     */
    private List<String> discoverSourceFiles(Path rootPath) throws IOException {
        List<String> sourceFiles = new ArrayList<>();

        if (Files.isRegularFile(rootPath)) {
            // 单个文件
            sourceFiles.add(rootPath.toString());
        } else if (Files.isDirectory(rootPath)) {
            // 目录 - 查找 C/C++ 源文件
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> {
                         String fileName = path.getFileName().toString().toLowerCase();
                         return fileName.endsWith(".c") || fileName.endsWith(".cpp") || 
                                fileName.endsWith(".cc") || fileName.endsWith(".cxx");
                     })
                     .map(Path::toString)
                     .forEach(sourceFiles::add);
            }
        }

        return sourceFiles;
    }

    /**
     * 显示单个模块的分析结果
     */
    private void displayModuleResult(SecurityScore score, TriageRecommendation recommendation) {
        String fileName = score.getFileName();
        int securityScore = score.getScore();
        
        // 根据分数显示不同颜色
        if (securityScore < 40) {
            printer.error(String.format("T1.1 SecurityScoringService 计算出：%s 得 %d/100 分", fileName, securityScore));
        } else if (securityScore < 70) {
            printer.warning(String.format("T1.1 SecurityScoringService 计算出：%s 得 %d/100 分（有 %d 个 Critical）", 
                fileName, securityScore, score.getCriticalCount()));
        } else {
            printer.success(String.format("T1.1 SecurityScoringService 计算出：%s 得 %d/100 分", fileName, securityScore));
        }

        // 显示 T1.2 决策
        printer.info(String.format("T1.2 TriageAdvisor 决策：%s", recommendation.toJson()));
        printer.blank();
    }

    /**
     * 显示详细建议
     */
    private void displayDetailedRecommendations(List<TriageRecommendation> recommendations) {
        printer.subheader("Detailed Recommendations");
        printer.blank();

        for (TriageRecommendation rec : recommendations) {
            SecurityScore score = rec.getSecurityScore();
            String riskIcon = getRiskIcon(score.getScore());
            
            printer.info(String.format("%s %s", riskIcon, rec.toString()));
            printer.keyValue("  Reasoning", rec.getReasoning());
            
            if (score.getTotalIssues() > 0) {
                printer.keyValue("  Issues", String.format("Critical: %d, High: %d, Medium: %d, Low: %d",
                    score.getCriticalCount(), score.getHighCount(), score.getMediumCount(), score.getLowCount()));
            }
            printer.blank();
        }
    }

    /**
     * 根据分数获取风险图标
     */
    private String getRiskIcon(int score) {
        if (score < 40) {
            return "🔴 [高风险]";
        } else if (score < 70) {
            return "🟡 [中风险]";
        } else {
            return "🟢 [低风险]";
        }
    }

    /**
     * Agent 主动回应 - 显示交互选项
     */
    private void showAgentResponse(List<TriageRecommendation> recommendations) {
        printer.blank();
        printer.header("🤖 Agent Response");
        printer.blank();

        // 生成分析摘要
        StringBuilder summary = new StringBuilder();
        summary.append("分析完成，报告已生成。我的战略分析摘要如下：\n");

        for (TriageRecommendation rec : recommendations) {
            SecurityScore score = rec.getSecurityScore();
            String riskIcon = getRiskIcon(score.getScore());
            
            summary.append(String.format("%s %s (%d/100分): 建议%s\n", 
                riskIcon, score.getFileName(), score.getScore(), rec.getRecommendation().getDescription()));
        }

        printer.info(summary.toString());
        printer.blank();

        // 显示交互选项
        printer.info("您希望我现在做什么？");
        printer.blank();

        // 根据建议类型显示选项
        List<String> options = new ArrayList<>();
        boolean hasRewrite = recommendations.stream().anyMatch(r -> r.getRecommendation() == TriageAdvisor.RecommendationType.REWRITE_RUST);
        boolean hasRepair = recommendations.stream().anyMatch(r -> r.getRecommendation() == TriageAdvisor.RecommendationType.REPAIR);

        int optionIndex = 1;
        if (hasRewrite) {
            printer.info(String.format("[%d] 为需要重写的模块生成 Rust 重构建议 (执行 /refactor)", optionIndex));
            options.add("refactor");
            optionIndex++;
        }

        if (hasRepair) {
            printer.info(String.format("[%d] 自动修复严重问题 (执行 /autofix)", optionIndex));
            options.add("autofix");
            optionIndex++;
        }

        printer.info(String.format("[%d] 进行AI修复建议", optionIndex));
        options.add("suggest");
        optionIndex++;

        printer.info(String.format("[%d] 暂时退出", optionIndex));
        options.add("exit");

        printer.blank();

        // 等待用户输入
        handleUserChoice(options, recommendations);
    }

    /**
     * 处理用户选择
     */
    private void handleUserChoice(List<String> options, List<TriageRecommendation> recommendations) {
        Scanner scanner = new Scanner(System.in);
        printer.info("请输入选项编号：");

        try {
            String input = scanner.nextLine().trim();
            int choice;

            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                printer.error("无效输入，请输入数字");
                return;
            }

            if (choice < 1 || choice > options.size()) {
                printer.error("选项超出范围");
                return;
            }

            String selectedOption = options.get(choice - 1);
            executeSelectedOption(selectedOption, recommendations);

        } catch (Exception e) {
            printer.error("处理用户选择时出错: " + e.getMessage());
        }
    }

    /**
     * 执行选中的选项
     */
    private void executeSelectedOption(String option, List<TriageRecommendation> recommendations) {
        ConfigManager configManager = parent.getConfigManager();

        switch (option) {
            case "refactor":
                printer.info("收到。正在为您生成 Rust 重构建议...");
                executeRefactorCommand(recommendations);
                break;

            case "autofix":
                printer.info("收到。正在为您自动修复严重问题...");
                executeAutoFixCommand(recommendations, configManager);
                break;

            case "suggest":
                printer.info("收到。正在为您生成AI修复建议...");
                executeSuggestCommand();
                break;

            case "exit":
                printer.success("感谢使用 HarmonySafeAgent！");
                break;

            default:
                printer.error("未知选项: " + option);
        }
    }

    /**
     * 执行重构命令
     */
    private void executeRefactorCommand(List<TriageRecommendation> recommendations) {
        // 找到需要重写的模块
        List<TriageRecommendation> rewriteRecs = recommendations.stream()
            .filter(r -> r.getRecommendation() == TriageAdvisor.RecommendationType.REWRITE_RUST)
            .toList();

        if (rewriteRecs.isEmpty()) {
            printer.warning("没有需要重写的模块");
            return;
        }

        for (TriageRecommendation rec : rewriteRecs) {
            printer.info("正在为 " + rec.getModule() + " 生成 Rust 重构建议...");
            // TODO: 集成 RefactorCommand
            printer.info("Rust 重构建议生成完成（功能开发中）");
        }
    }

    /**
     * 执行自动修复命令
     */
    private void executeAutoFixCommand(List<TriageRecommendation> recommendations, ConfigManager configManager) {
        try {
            // 初始化自动修复组件
            LLMClient llmClient = new LLMClient(configManager);
            File workDir = new File(System.getProperty("user.dir"));
            CodeValidator codeValidator = new CodeValidator(toolExecutor, workDir);
            AutoFixOrchestrator autoFixOrchestrator = new AutoFixOrchestrator(llmClient, codeValidator);
            ChangeManager changeManager = new ChangeManager();

            // 找到需要修复的模块
            List<TriageRecommendation> repairRecs = recommendations.stream()
                .filter(r -> r.getRecommendation() == TriageAdvisor.RecommendationType.REPAIR)
                .toList();

            if (repairRecs.isEmpty()) {
                printer.warning("没有需要修复的模块");
                return;
            }

            printer.info("开始 P-C-R-T 循环自动修复...");
            
            for (TriageRecommendation rec : repairRecs) {
                printer.info("正在修复 " + rec.getModule() + "...");
                // TODO: 集成 AutoFixOrchestrator
                printer.info("自动修复完成（功能开发中）");
            }

        } catch (Exception e) {
            printer.error("自动修复初始化失败: " + e.getMessage());
        }
    }

    /**
     * 执行建议命令
     */
    private void executeSuggestCommand() {
        printer.info("正在生成AI修复建议...");
        // TODO: 集成 SuggestCommand
        printer.info("AI修复建议生成完成（功能开发中）");
    }
}