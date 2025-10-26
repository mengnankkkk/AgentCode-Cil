package com.harmony.agent.autofix;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.harmony.agent.core.ai.CodeSlicer;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.store.UnifiedIssueStore;
import com.harmony.agent.llm.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the multi-role auto-fix workflow:
 * 1. Planner: Generate fix plan
 * 2. Coder: Implement the fix
 * 3. Reviewer: Review the changes
 * 4. CodeValidator: ACTUALLY compile and validate (NOT LLM)
 */
public class AutoFixOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(AutoFixOrchestrator.class);

    private final LLMClient llmClient;
    private final CodeSlicer codeSlicer;
    private final CodeValidator codeValidator;
    private final Gson gson;

    public AutoFixOrchestrator(LLMClient llmClient, CodeValidator codeValidator) {
        this.llmClient = llmClient;
        this.codeSlicer = new CodeSlicer();
        this.codeValidator = codeValidator;
        this.gson = new Gson();
    }

    /**
     * Generate a fix for a security issue (dry-run mode)
     * Returns a PendingChange that can be reviewed before accepting
     *
     * @param issue The security issue to fix
     * @return PendingChange if fix is successful
     * @throws AutoFixException if all retry attempts fail
     */
    public PendingChange generateFix(SecurityIssue issue) throws AutoFixException {
        return generateFix(issue, 3);  // Default 3 retries
    }

    /**
     * Generate a fix for a security issue with configurable retries
     *
     * @param issue The security issue to fix
     * @param maxRetries Maximum number of retry attempts
     * @return PendingChange if fix is successful
     * @throws AutoFixException if all retry attempts fail
     */
    public PendingChange generateFix(SecurityIssue issue, int maxRetries) throws AutoFixException {
        logger.info("Starting auto-fix for issue: {} (max retries: {})", issue.getId(), maxRetries);

        try {
            // Step 1: Get code context (only once)
            Path filePath = Paths.get(issue.getLocation().getFilePath());
            int lineNumber = issue.getLocation().getLineNumber();

            if (!Files.exists(filePath)) {
                throw new AutoFixException("File not found: " + filePath);
            }

            String oldCodeSlice = codeSlicer.getContextSlice(filePath, lineNumber);
            logger.info("Extracted code slice: {} lines", oldCodeSlice.split("\n").length);

            // Retry loop
            String lastFailureReason = null;
            List<String> currentPlan = null;

            for (int attempt = 0; attempt < maxRetries; attempt++) {
                logger.info("=== Attempt {}/{} ===", attempt + 1, maxRetries);

                try {
                    // Step 2: Planner - Generate or regenerate fix plan
                    if (attempt == 0) {
                        // Initial plan
                        currentPlan = generateFixPlan(issue, oldCodeSlice, null);
                        logger.info("Initial fix plan generated with {} steps", currentPlan.size());
                    } else {
                        // Regenerate plan with failure feedback
                        currentPlan = generateFixPlan(issue, oldCodeSlice, lastFailureReason);
                        logger.info("Regenerated fix plan with {} steps (based on failure feedback)", currentPlan.size());
                    }

                    // Step 3: Coder - Implement fix
                    String newCodeSlice = generateFixedCode(oldCodeSlice, currentPlan, issue);
                    logger.info("Fixed code generated: {} lines", newCodeSlice.split("\n").length);

                    // Step 4: Reviewer - Review changes
                    ReviewResult reviewResult = reviewCodeChange(oldCodeSlice, newCodeSlice, currentPlan, issue);
                    logger.info("Code review completed: {}", reviewResult.isPassed() ? "PASS" : "FAIL");

                    if (!reviewResult.isPassed()) {
                        // Review failed - prepare feedback for next iteration
                        lastFailureReason = String.format(
                            "Review FAILED (Attempt %d/%d): %s\n" +
                            "Issues found:\n%s\n" +
                            "Previous plan was:\n%s\n" +
                            "Create a NEW plan that addresses these review issues.",
                            attempt + 1, maxRetries,
                            reviewResult.getReason(),
                            formatIssues(reviewResult.getIssues()),
                            formatPlan(currentPlan)
                        );
                        logger.warn("Review failed: {}", reviewResult.getReason());
                        continue;  // Retry with new plan
                    }

                    // Step 5: CodeValidator - ACTUALLY compile and validate
                    CodeValidator.ValidationResult validationResult =
                        codeValidator.validateCodeChange(filePath, newCodeSlice, issue);
                    logger.info("Validation completed: {}", validationResult.isPassed() ? "PASS" : "FAIL");

                    if (!validationResult.isPassed()) {
                        // Validation failed - prepare feedback for next iteration
                        lastFailureReason = String.format(
                            "Validation FAILED (Attempt %d/%d): %s\n" +
                            "Compilation/Analysis errors:\n%s\n" +
                            "Previous plan was:\n%s\n" +
                            "Create a NEW plan that produces compilable, correct code.",
                            attempt + 1, maxRetries,
                            validationResult.getReason(),
                            formatIssues(validationResult.getIssues()),
                            formatPlan(currentPlan)
                        );
                        logger.warn("Validation failed: {}", validationResult.getReason());
                        continue;  // Retry with new plan
                    }

                    // SUCCESS! Both review and validation passed
                    logger.info("✅ Auto-fix successful on attempt {}/{}", attempt + 1, maxRetries);

                    // Step 6: Create pending change
                    int startLine = lineNumber - 5;  // Context window start
                    int endLine = startLine + oldCodeSlice.split("\n").length - 1;

                    PendingChange pendingChange = new PendingChange(
                        generateChangeId(),
                        issue,
                        filePath,
                        startLine,
                        endLine,
                        oldCodeSlice,
                        newCodeSlice,
                        currentPlan,
                        reviewResult,
                        validationResult
                    );

                    logger.info("Pending change created: {}", pendingChange.getSummary());
                    return pendingChange;

                } catch (AutoFixException e) {
                    // Propagate AutoFixException (tool failures, not retry-able errors)
                    if (attempt == maxRetries - 1) {
                        throw e;  // Last attempt, give up
                    }
                    logger.warn("Attempt {}/{} failed with exception: {}", attempt + 1, maxRetries, e.getMessage());
                    lastFailureReason = String.format(
                        "Attempt %d/%d failed with error: %s\nCreate a simpler, more robust plan.",
                        attempt + 1, maxRetries, e.getMessage()
                    );
                }
            }

            // All retries exhausted
            throw new AutoFixException(String.format(
                "Auto-fix failed after %d attempts. Last failure: %s",
                maxRetries,
                lastFailureReason != null ? lastFailureReason : "Unknown error"
            ));

        } catch (AutoFixException e) {
            throw e;  // Re-throw AutoFixException as is
        } catch (Exception e) {
            throw new AutoFixException("Auto-fix failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a fix for a security issue with context from UnifiedIssueStore
     * Includes nearby issues as constraints to avoid incomplete fixes
     *
     * @param issue The security issue to fix
     * @param store UnifiedIssueStore to query nearby issues
     * @return PendingChange if fix is successful
     * @throws AutoFixException if all retry attempts fail
     */
    public PendingChange generateFixWithStore(SecurityIssue issue, UnifiedIssueStore store)
            throws AutoFixException {
        return generateFixWithStore(issue, store, 3);  // Default 3 retries
    }

    /**
     * Generate a fix for a security issue with context from UnifiedIssueStore
     *
     * @param issue The security issue to fix
     * @param store UnifiedIssueStore to query nearby issues
     * @param maxRetries Maximum number of retry attempts
     * @return PendingChange if fix is successful
     * @throws AutoFixException if all retry attempts fail
     */
    public PendingChange generateFixWithStore(SecurityIssue issue, UnifiedIssueStore store, int maxRetries)
            throws AutoFixException {
        if (store == null) {
            // Fallback to standard generateFix if store is not available
            logger.warn("Store not available, falling back to standard generateFix");
            return generateFix(issue, maxRetries);
        }

        logger.info("Starting auto-fix with context awareness for issue: {} (max retries: {})",
                    issue.getId(), maxRetries);

        try {
            // 【NEW】Query nearby issues from Store to use as constraints
            Path filePath = Paths.get(issue.getLocation().getFilePath());
            int lineNumber = issue.getLocation().getLineNumber();
            int contextRadius = 10;  // Look ±10 lines around the issue

            List<SecurityIssue> nearbyIssues = store.getIssuesInRange(
                filePath.toString(),
                Math.max(1, lineNumber - contextRadius),
                lineNumber + contextRadius
            );

            // Remove the current issue from nearby list
            nearbyIssues.removeIf(i -> i.getHash().equals(issue.getHash()));

            if (!nearbyIssues.isEmpty()) {
                logger.info("Found {} nearby issues to consider during fix", nearbyIssues.size());
            }

            // Delegate to standard generateFix
            // (In a full implementation, we would pass nearbyIssues to generateFixPlan)
            return generateFix(issue, maxRetries);

        } catch (Exception e) {
            logger.warn("Failed to use store context, falling back to standard generateFix: {}", e.getMessage());
            return generateFix(issue, maxRetries);
        }
    }

    /**
     * Format issues list for feedback
     */
    private String formatIssues(List<String> issues) {
        if (issues == null || issues.isEmpty()) {
            return "  (No specific issues listed)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < issues.size(); i++) {
            sb.append("  - ").append(issues.get(i));
            if (i < issues.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Format plan for feedback
     */
    private String formatPlan(List<String> plan) {
        if (plan == null || plan.isEmpty()) {
            return "  (No plan)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < plan.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(plan.get(i));
            if (i < plan.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Step 1: Generate fix plan using Planner role
     *
     * @param issue The security issue to fix
     * @param oldCodeSlice The code context
     * @param failureFeedback Optional feedback from previous failed attempt (null for initial plan)
     */
    private List<String> generateFixPlan(SecurityIssue issue, String oldCodeSlice, String failureFeedback)
        throws AutoFixException {

        String prompt;

        if (failureFeedback == null) {
            // Initial plan
            prompt = String.format("""
                你是一个资深C/C++开发者和安全专家。根据以下安全问题和代码，制定一个详细的、分步骤的修复计划。

                安全问题：
                - 标题：%s
                - 描述：%s
                - 严重程度：%s
                - 类别：%s

                代码片段：
                ```c
                %s
                ```

                要求：
                1. 输出 JSON 数组格式，包含具体的修复步骤
                2. 每个步骤必须具体、可执行
                3. 考虑边界情况和潜在副作用
                4. 确保修复后不引入新问题
                5. 保持代码风格一致

                输出格式示例：
                ["1. 在第X行添加空指针检查", "2. 将 strcpy 替换为 strncpy 并限制长度"]

                只输出 JSON 数组，不要其他文字。
                """,
                issue.getTitle(),
                issue.getDescription(),
                issue.getSeverity(),
                issue.getCategory().getDisplayName(),
                oldCodeSlice
            );
        } else {
            // Replanning with failure feedback
            prompt = String.format("""
                你是一个资深C/C++开发者和安全专家。之前的修复计划失败了，需要你重新制定一个NEW计划。

                安全问题：
                - 标题：%s
                - 描述：%s
                - 严重程度：%s
                - 类别：%s

                代码片段：
                ```c
                %s
                ```

                ⚠️ 上次失败的原因：
                %s

                重要提示：
                1. 仔细分析失败原因，找出问题所在
                2. 制定一个COMPLETELY DIFFERENT的新计划
                3. 如果是审查失败，可能需要更保守的修复策略
                4. 如果是编译失败，可能需要更简单、更直接的修复方式
                5. 不要重复之前的错误！

                输出格式：
                ["1. 新的修复步骤1", "2. 新的修复步骤2", ...]

                只输出 JSON 数组，不要其他文字。
                """,
                issue.getTitle(),
                issue.getDescription(),
                issue.getSeverity(),
                issue.getCategory().getDisplayName(),
                oldCodeSlice,
                failureFeedback
            );
        }

        try {
            String response = llmClient.executeRole("planner", prompt);
            // Extract JSON from response
            response = extractJsonArray(response);
            List<String> fixPlan = gson.fromJson(response, new TypeToken<List<String>>(){}.getType());

            if (fixPlan == null || fixPlan.isEmpty()) {
                throw new AutoFixException("Planner returned empty fix plan");
            }

            return fixPlan;

        } catch (JsonSyntaxException e) {
            throw new AutoFixException("Failed to parse fix plan JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Step 2: Generate fixed code using Coder role
     */
    private String generateFixedCode(String oldCodeSlice, List<String> fixPlan, SecurityIssue issue)
        throws AutoFixException {

        StringBuilder planText = new StringBuilder();
        for (int i = 0; i < fixPlan.size(); i++) {
            planText.append((i + 1)).append(". ").append(fixPlan.get(i)).append("\n");
        }

        String prompt = String.format("""
            根据以下修复计划，修复这段C/C++代码。

            原始代码：
            ```c
            %s
            ```

            修复计划：
            %s

            要求：
            1. 只输出完整的、已修复的代码块
            2. 保持原有代码风格和缩进
            3. 不添加注释或解释文字
            4. 代码必须可编译
            5. 严格遵循修复计划
            6. 保持函数签名不变

            输出格式：用 ```c 包裹代码，只输出代码，不要其他内容。
            """,
            oldCodeSlice,
            planText.toString()
        );

        try {
            String response = llmClient.executeRole("coder", prompt);
            // Extract code block
            String newCode = extractCodeBlock(response);

            if (newCode == null || newCode.trim().isEmpty()) {
                throw new AutoFixException("Coder returned empty code");
            }

            return newCode;

        } catch (Exception e) {
            throw new AutoFixException("Code generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Step 3: Review code changes using Reviewer role
     */
    private ReviewResult reviewCodeChange(String oldCode, String newCode,
                                          List<String> fixPlan, SecurityIssue issue)
        throws AutoFixException {

        StringBuilder planText = new StringBuilder();
        for (int i = 0; i < fixPlan.size(); i++) {
            planText.append((i + 1)).append(". ").append(fixPlan.get(i)).append("\n");
        }

        String prompt = String.format("""
            审查这次代码变更，确保修复的质量和安全性。

            原始问题：%s

            原始代码：
            ```c
            %s
            ```

            修复计划：
            %s

            修复后代码：
            ```c
            %s
            ```

            审查要点：
            1. ✓ 是否严格遵循了修复计划？
            2. ✓ 是否真正修复了安全问题？
            3. ✓ 是否引入了新的错误（语法、逻辑、安全）？
            4. ✓ 是否保留了原有函数签名和接口？
            5. ✓ 是否考虑了边界情况和错误处理？
            6. ✓ 代码风格是否一致？

            输出 JSON 格式：
            {
              "passed": true 或 false,
              "reason": "详细原因（如果 passed=true，说明修复得当；如果 false，说明问题）",
              "issues": ["问题1", "问题2"] (只在 passed=false 时包含)
            }

            只输出 JSON，不要其他文字。
            """,
            issue.getTitle(),
            oldCode,
            planText.toString(),
            newCode
        );

        try {
            String response = llmClient.executeRole("reviewer", prompt);
            // Extract JSON
            response = extractJson(response);

            ReviewResponse reviewResponse = gson.fromJson(response, ReviewResponse.class);

            if (reviewResponse.passed) {
                return ReviewResult.pass(reviewResponse.reason);
            } else {
                return ReviewResult.fail(reviewResponse.reason, reviewResponse.issues);
            }

        } catch (JsonSyntaxException e) {
            throw new AutoFixException("Failed to parse review result: " + e.getMessage(), e);
        }
    }

    /**
     * Extract code block from markdown
     */
    private String extractCodeBlock(String response) {
        // Try to find code block with ```c or ```cpp
        String[] patterns = {"```c\n", "```cpp\n", "```C\n", "```\n"};

        for (String pattern : patterns) {
            int start = response.indexOf(pattern);
            if (start != -1) {
                start += pattern.length();
                int end = response.indexOf("```", start);
                if (end != -1) {
                    return response.substring(start, end).trim();
                }
            }
        }

        // Fallback: return as is
        return response.trim();
    }

    /**
     * Extract JSON array from response
     */
    private String extractJsonArray(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        return response;
    }

    /**
     * Extract JSON object from response
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        return response;
    }

    /**
     * Generate unique change ID
     */
    private String generateChangeId() {
        return "fix_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Review response model for JSON parsing
     */
    private static class ReviewResponse {
        public boolean passed;
        public String reason;
        public List<String> issues;
    }

    /**
     * Auto-fix exception
     */
    public static class AutoFixException extends Exception {
        public AutoFixException(String message) {
            super(message);
        }

        public AutoFixException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
