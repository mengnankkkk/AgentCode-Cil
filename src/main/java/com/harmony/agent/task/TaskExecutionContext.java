package com.harmony.agent.task;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task Execution Context Manager
 * Store original requirements, analysis results, completed task outputs, etc.
 * 集成了缓存管理和 AI 记忆支持
 */
public class TaskExecutionContext {
    private static final Logger logger = LoggerFactory.getLogger(TaskExecutionContext.class);

    private String originalRequirement;
    private String analysisResult;
    private Map<String, String> decisions;
    private List<String> constraints;
    private List<String> risks;
    private Map<Integer, TaskResult> taskResults;
    private TaskContextCacheManager cacheManager;  // 缓存管理器
    private String sessionId;  // 关联的会话 ID

    public TaskExecutionContext(String requirement) {
        this.originalRequirement = requirement;
        this.decisions = new HashMap<>();
        this.constraints = new ArrayList<>();
        this.risks = new ArrayList<>();
        this.taskResults = new HashMap<>();
        this.cacheManager = new TaskContextCacheManager();
        this.sessionId = cacheManager.getCurrentSessionId();

        // 立即缓存需求
        cacheManager.cacheRequirement(requirement);
    }

    public String getOriginalRequirement() {
        return originalRequirement;
    }

    public void setAnalysisResult(String analysisResult) {
        this.analysisResult = analysisResult;
        this.extractMetadataFromAnalysis();
        // 缓存分析结果
        if (cacheManager != null) {
            cacheManager.cacheAnalysisResult(analysisResult);
        }
    }

    public String getAnalysisResult() {
        return analysisResult;
    }

    public void addDecision(String decision, String reasoning) {
        decisions.put(decision, reasoning);
    }

    public Map<String, String> getDecisions() {
        return new HashMap<>(decisions);
    }

    public void addConstraint(String constraint) {
        constraints.add(constraint);
    }

    public List<String> getConstraints() {
        return new ArrayList<>(constraints);
    }

    public void addRisk(String risk) {
        risks.add(risk);
    }

    public List<String> getRisks() {
        return new ArrayList<>(risks);
    }

    public void recordTaskResult(int taskId, String taskDescription, String output, boolean success) {
        taskResults.put(taskId, new TaskResult(taskDescription, output, success));
        // 缓存任务结果
        if (cacheManager != null) {
            cacheManager.cacheTaskResult(taskId, taskDescription, output, success);
        }
    }

    public TaskResult getTaskResult(int taskId) {
        return taskResults.get(taskId);
    }

    public List<TaskResult> getCompletedTaskResults() {
        return taskResults.values().stream()
            .filter(TaskResult::isSuccess)
            .toList();
    }

    /**
     * Extract metadata from analysis result (constraints, risks, etc.)
     */
    private void extractMetadataFromAnalysis() {
        if (analysisResult == null || analysisResult.isEmpty()) {
            return;
        }

        String[] lines = analysisResult.split("\n");
        String currentSection = null;

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("##")) {
                currentSection = line.substring(2).trim();
            } else if (!line.isEmpty()) {
                if ("Constraints".equals(currentSection) && line.startsWith("-")) {
                    constraints.add(line.substring(1).trim());
                } else if ("Risks".equals(currentSection) && line.startsWith("-")) {
                    risks.add(line.substring(1).trim());
                }
            }
        }

        logger.info("Extracted {} constraints and {} risks from analysis", constraints.size(), risks.size());
    }

    /**
     * Generate complete execution context report
     */
    public String generateContextReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("## Task Execution Context\n\n");

        sb.append("### Original Requirement\n");
        sb.append(originalRequirement).append("\n\n");

        if (!constraints.isEmpty()) {
            sb.append("### Constraints\n");
            constraints.forEach(c -> sb.append("- ").append(c).append("\n"));
            sb.append("\n");
        }

        if (!risks.isEmpty()) {
            sb.append("### Risks\n");
            risks.forEach(r -> sb.append("- ").append(r).append("\n"));
            sb.append("\n");
        }

        if (!decisions.isEmpty()) {
            sb.append("### Key Decisions\n");
            decisions.forEach((decision, reasoning) ->
                sb.append("- ").append(decision).append("\n  Reasoning: ").append(reasoning).append("\n")
            );
            sb.append("\n");
        }

        if (!taskResults.isEmpty()) {
            sb.append("### Completed Tasks\n");
            taskResults.forEach((taskId, result) -> {
                sb.append(String.format("- Task %d: %s\n", taskId, result.description));
                if (result.success) {
                    sb.append("  Status: SUCCESS\n");
                } else {
                    sb.append("  Status: FAILED\n");
                }
            });
        }

        return sb.toString();
    }

    // ==================== 缓存相关方法 ====================

    /**
     * 获取缓存管理器
     */
    public TaskContextCacheManager getCacheManager() {
        return cacheManager;
    }

    /**
     * 获取会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 缓存任务列表
     */
    public void cacheTaskList(List<?> tasks) {
        if (cacheManager != null && tasks != null) {
            cacheManager.cacheTaskList((List<Task>) tasks);
        }
    }

    /**
     * 获取会话上下文摘要（用于 AI Prompt 注入）
     */
    public String getSessionContextSummary() {
        if (cacheManager != null) {
            return cacheManager.buildSessionContextSummary();
        }
        return "";
    }

    /**
     * 获取缓存统计
     */
    public String getCacheStats() {
        if (cacheManager != null) {
            return cacheManager.getCacheStats();
        }
        return "缓存管理器未初始化";
    }

    /**
     * Task execution result
     */
    public static class TaskResult {
        public final String description;
        public final String output;
        public final boolean success;

        public TaskResult(String description, String output, boolean success) {
            this.description = description;
            this.output = output;
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
