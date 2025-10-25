package com.harmony.agent.core.ai;

import com.harmony.agent.core.model.CodeLocation;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.provider.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code Reviewer - AI驱动的代码审查引擎
 * 灵感来自 kodus-ai，提供上下文感知的代码审查
 *
 * 功能：
 * - 发现安全漏洞
 * - 识别潜在 bug
 * - 检查代码异味
 * - 验证最佳实践
 * - 性能问题分析
 */
public class CodeReviewer {

    private static final Logger logger = LoggerFactory.getLogger(CodeReviewer.class);

    // 审查配置
    private static final double REVIEW_TEMPERATURE = 0.3; // 较低温度以获得更一致的分析
    private static final int REVIEW_MAX_TOKENS = 4000;

    private final LLMProvider llmProvider;
    private final String model;

    /**
     * 审查焦点类型
     */
    public enum ReviewFocus {
        ALL("全面审查"),
        SECURITY("安全性"),
        PERFORMANCE("性能"),
        MAINTAINABILITY("可维护性"),
        BEST_PRACTICES("最佳实践"),
        CODE_SMELLS("代码异味");

        private final String displayName;

        ReviewFocus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 审查结果
     */
    public static class ReviewResult {
        private final List<SecurityIssue> issues;
        private final String summary;
        private final int criticalCount;
        private final int highCount;
        private final int mediumCount;
        private final int lowCount;

        public ReviewResult(List<SecurityIssue> issues, String summary) {
            this.issues = new ArrayList<>(issues);
            this.summary = summary;

            // 统计各级别问题数量
            int critical = 0, high = 0, medium = 0, low = 0;
            for (SecurityIssue issue : issues) {
                switch (issue.getSeverity()) {
                    case CRITICAL -> critical++;
                    case HIGH -> high++;
                    case MEDIUM -> medium++;
                    case LOW -> low++;
                }
            }
            this.criticalCount = critical;
            this.highCount = high;
            this.mediumCount = medium;
            this.lowCount = low;
        }

        public List<SecurityIssue> getIssues() {
            return issues;
        }

        public String getSummary() {
            return summary;
        }

        public int getCriticalCount() {
            return criticalCount;
        }

        public int getHighCount() {
            return highCount;
        }

        public int getMediumCount() {
            return mediumCount;
        }

        public int getLowCount() {
            return lowCount;
        }

        public int getTotalCount() {
            return issues.size();
        }

        public boolean hasIssues() {
            return !issues.isEmpty();
        }
    }

    /**
     * 构造函数
     */
    public CodeReviewer(LLMProvider llmProvider, String model) {
        this.llmProvider = llmProvider;
        this.model = model;

        logger.info("CodeReviewer initialized with provider: {}, model: {}",
            llmProvider.getProviderName(), model);
    }

    /**
     * 审查单个文件
     *
     * @param filePath 文件路径
     * @param focus 审查焦点
     * @return 审查结果
     */
    public ReviewResult reviewFile(Path filePath, ReviewFocus focus) {
        logger.info("Reviewing file: {} with focus: {}", filePath, focus);

        try {
            // 1. 读取文件内容
            String code = Files.readString(filePath);

            if (code.trim().isEmpty()) {
                logger.warn("File is empty: {}", filePath);
                return new ReviewResult(List.of(), "文件为空，无需审查");
            }

            // 2. 构建审查提示词
            String prompt = buildReviewPrompt(filePath.getFileName().toString(), code, focus);

            // 3. 调用 LLM 进行审查
            LLMRequest request = LLMRequest.builder()
                .model(model)
                .temperature(REVIEW_TEMPERATURE)
                .maxTokens(REVIEW_MAX_TOKENS)
                .addSystemMessage(buildSystemPrompt(focus))
                .addUserMessage(prompt)
                .build();

            logger.debug("Sending review request to LLM");
            LLMResponse response = llmProvider.sendRequest(request);

            // 4. 检查响应
            if (!response.isSuccess()) {
                logger.error("LLM review failed: {}", response.getErrorMessage());
                return new ReviewResult(List.of(),
                    "审查失败: " + response.getErrorMessage());
            }

            String reviewContent = response.getContent();
            if (reviewContent == null || reviewContent.trim().isEmpty()) {
                logger.error("LLM returned empty response");
                return new ReviewResult(List.of(), "LLM 返回空响应");
            }

            logger.info("Review completed successfully ({} tokens used)",
                response.getTotalTokens());

            // 5. 解析审查结果
            return parseReviewResult(reviewContent, filePath);

        } catch (IOException e) {
            logger.error("Failed to read file for review: {}", filePath, e);
            return new ReviewResult(List.of(),
                "读取文件失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Review failed", e);
            return new ReviewResult(List.of(),
                "审查失败: " + e.getMessage());
        }
    }

    /**
     * 审查多个文件（项目级别）
     *
     * @param filePaths 文件路径列表
     * @param focus 审查焦点
     * @return 合并的审查结果
     */
    public ReviewResult reviewFiles(List<Path> filePaths, ReviewFocus focus) {
        logger.info("Reviewing {} files with focus: {}", filePaths.size(), focus);

        List<SecurityIssue> allIssues = new ArrayList<>();
        StringBuilder summarySb = new StringBuilder();

        for (Path filePath : filePaths) {
            ReviewResult result = reviewFile(filePath, focus);
            allIssues.addAll(result.getIssues());

            if (result.hasIssues()) {
                summarySb.append(String.format("\n%s: 发现 %d 个问题",
                    filePath.getFileName(), result.getTotalCount()));
            }
        }

        String summary = String.format("审查了 %d 个文件，共发现 %d 个问题%s",
            filePaths.size(), allIssues.size(), summarySb.toString());

        return new ReviewResult(allIssues, summary);
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(ReviewFocus focus) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a senior code reviewer with expertise in security, performance, and software engineering best practices. ");
        prompt.append("Your task is to review code and identify issues.\n\n");

        prompt.append("Review Focus: ").append(focus.getDisplayName()).append("\n\n");

        prompt.append("Guidelines:\n");
        prompt.append("1. Identify real issues, not nitpicks\n");
        prompt.append("2. Provide specific line numbers when possible\n");
        prompt.append("3. Classify severity: CRITICAL, HIGH, MEDIUM, LOW\n");
        prompt.append("4. Categorize issues: SECURITY, PERFORMANCE, MEMORY, LOGIC, STYLE, BEST_PRACTICE\n");
        prompt.append("5. Explain WHY it's an issue and HOW to fix it\n");
        prompt.append("6. Be constructive and educational\n\n");

        switch (focus) {
            case SECURITY -> prompt.append("Focus on: SQL injection, XSS, CSRF, authentication bypass, insecure crypto, hardcoded secrets, etc.\n");
            case PERFORMANCE -> prompt.append("Focus on: inefficient algorithms, memory leaks, unnecessary computations, blocking operations, etc.\n");
            case MAINTAINABILITY -> prompt.append("Focus on: code complexity, duplication, unclear naming, poor structure, lack of documentation, etc.\n");
            case BEST_PRACTICES -> prompt.append("Focus on: language idioms, design patterns, error handling, resource management, etc.\n");
            case CODE_SMELLS -> prompt.append("Focus on: long methods, large classes, feature envy, data clumps, primitive obsession, etc.\n");
            case ALL -> prompt.append("Review all aspects: security, performance, maintainability, best practices, and code quality.\n");
        }

        prompt.append("\nOutput format:\n");
        prompt.append("For each issue, output in this exact format:\n");
        prompt.append("[ISSUE]\n");
        prompt.append("SEVERITY: <CRITICAL|HIGH|MEDIUM|LOW>\n");
        prompt.append("CATEGORY: <SECURITY|PERFORMANCE|MEMORY|LOGIC|STYLE|BEST_PRACTICE>\n");
        prompt.append("LINE: <line_number>\n");
        prompt.append("TITLE: <brief_title>\n");
        prompt.append("DESCRIPTION: <detailed_explanation>\n");
        prompt.append("FIX: <how_to_fix>\n");
        prompt.append("[/ISSUE]\n\n");

        prompt.append("If no issues found, respond with: NO_ISSUES_FOUND");

        return prompt.toString();
    }

    /**
     * 构建审查提示词
     */
    private String buildReviewPrompt(String fileName, String code, ReviewFocus focus) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Please review the following code file:\n\n");
        prompt.append("File: ").append(fileName).append("\n\n");
        prompt.append("```\n");
        prompt.append(code);
        prompt.append("\n```\n\n");

        prompt.append("Review focus: ").append(focus.getDisplayName()).append("\n");
        prompt.append("Identify all issues and output in the specified format.");

        return prompt.toString();
    }

    /**
     * 解析审查结果
     */
    private ReviewResult parseReviewResult(String reviewContent, Path filePath) {
        List<SecurityIssue> issues = new ArrayList<>();

        // 检查是否没有问题
        if (reviewContent.contains("NO_ISSUES_FOUND")) {
            logger.info("No issues found in: {}", filePath);
            return new ReviewResult(issues, "代码审查通过，未发现问题");
        }

        // 使用正则表达式解析问题
        Pattern issuePattern = Pattern.compile(
            "\\[ISSUE\\]\\s*" +
            "SEVERITY:\\s*(\\w+)\\s*" +
            "CATEGORY:\\s*(\\w+)\\s*" +
            "LINE:\\s*(\\d+)\\s*" +
            "TITLE:\\s*(.+?)\\s*" +
            "DESCRIPTION:\\s*(.+?)\\s*" +
            "FIX:\\s*(.+?)\\s*" +
            "\\[/ISSUE\\]",
            Pattern.DOTALL
        );

        Matcher matcher = issuePattern.matcher(reviewContent);

        while (matcher.find()) {
            try {
                String severityStr = matcher.group(1).trim();
                String categoryStr = matcher.group(2).trim();
                int lineNumber = Integer.parseInt(matcher.group(3).trim());
                String title = matcher.group(4).trim();
                String description = matcher.group(5).trim();
                String fix = matcher.group(6).trim();

                // 转换严重程度
                IssueSeverity severity = parseIssueSeverity(severityStr);

                // 转换类别
                IssueCategory category = parseIssueCategory(categoryStr);

                // 创建 CodeLocation
                CodeLocation codeLocation = new CodeLocation(filePath.toString(), lineNumber);

                // 创建 SecurityIssue 使用 Builder 模式
                SecurityIssue issue = new SecurityIssue.Builder()
                    .id(UUID.randomUUID().toString())
                    .title(title)
                    .description(description + "\n\n修复建议: " + fix)
                    .severity(severity)
                    .category(category)
                    .location(codeLocation)
                    .analyzer("CodeReview")
                    .build();

                issues.add(issue);

                logger.debug("Parsed issue: {} at line {}", title, lineNumber);

            } catch (Exception e) {
                logger.warn("Failed to parse issue: {}", e.getMessage());
            }
        }

        String summary = String.format("代码审查完成，发现 %d 个问题", issues.size());

        return new ReviewResult(issues, summary);
    }

    /**
     * 解析严重程度字符串
     */
    private IssueSeverity parseIssueSeverity(String severityStr) {
        return switch (severityStr.toUpperCase()) {
            case "CRITICAL" -> IssueSeverity.CRITICAL;
            case "HIGH" -> IssueSeverity.HIGH;
            case "MEDIUM" -> IssueSeverity.MEDIUM;
            case "LOW" -> IssueSeverity.LOW;
            default -> IssueSeverity.INFO;
        };
    }

    /**
     * 解析问题类别字符串
     */
    private IssueCategory parseIssueCategory(String categoryStr) {
        return switch (categoryStr.toUpperCase()) {
            case "SECURITY" -> IssueCategory.CODE_QUALITY;  // 通用安全问题
            case "PERFORMANCE" -> IssueCategory.CODE_QUALITY;
            case "MEMORY" -> IssueCategory.MEMORY_LEAK;
            case "LOGIC" -> IssueCategory.CODE_QUALITY;
            case "STYLE" -> IssueCategory.CODE_QUALITY;
            case "BEST_PRACTICE" -> IssueCategory.CODE_QUALITY;
            default -> IssueCategory.UNKNOWN;
        };
    }

    /**
     * 检查 LLM 是否可用
     */
    public boolean isAvailable() {
        return llmProvider.isAvailable();
    }

    /**
     * 获取提供者名称
     */
    public String getProviderName() {
        return llmProvider.getProviderName();
    }

    /**
     * 获取模型名称
     */
    public String getModelName() {
        return model;
    }
}
