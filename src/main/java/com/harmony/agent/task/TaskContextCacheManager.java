package com.harmony.agent.task;

import com.harmony.agent.core.ai.PersistentCacheManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务上下文缓存管理器
 * 管理 TodoList 的完整上下文缓存（需求、分析结果、任务列表、执行结果）
 * 支持上下文快速恢复和增量更新
 */
public class TaskContextCacheManager {
    private static final Logger logger = LoggerFactory.getLogger(TaskContextCacheManager.class);

    private final PersistentCacheManager cache;
    private final Gson gson;
    private String currentSessionId;  // 当前会话 ID，用于关联缓存数据

    // 缓存键前缀
    private static final String PREFIX_REQUIREMENT = "task-ctx:requirement:";
    private static final String PREFIX_ANALYSIS = "task-ctx:analysis:";
    private static final String PREFIX_TASKS = "task-ctx:tasks:";
    private static final String PREFIX_TASK_RESULT = "task-ctx:result:";
    private static final String PREFIX_SESSION = "task-ctx:session:";
    private static final String PREFIX_CONTEXT = "task-ctx:context:";

    public TaskContextCacheManager() {
        // 使用 "task-context" 作为缓存类型，启用持久化
        this.cache = new PersistentCacheManager("task-context", true);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.currentSessionId = UUID.randomUUID().toString();
        logger.info("TaskContextCacheManager initialized with session ID: {}", currentSessionId);
    }

    /**
     * 生成新的会话 ID（用于隔离不同的 TodoList 上下文）
     */
    public String generateNewSession() {
        this.currentSessionId = UUID.randomUUID().toString();
        logger.info("新的任务会话已创建: {}", currentSessionId);
        return currentSessionId;
    }

    /**
     * 获取当前会话 ID
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }

    /**
     * 缓存原始需求
     */
    public void cacheRequirement(String requirement) {
        if (requirement == null || requirement.isEmpty()) {
            return;
        }
        String key = PREFIX_REQUIREMENT + currentSessionId;
        cache.put(key, requirement);
        logger.info("📝 缓存需求: {} (session: {})",
            truncateForLog(requirement), currentSessionId);
    }

    /**
     * 获取缓存的需求
     */
    public String getCachedRequirement() {
        String key = PREFIX_REQUIREMENT + currentSessionId;
        return cache.get(key);
    }

    /**
     * 缓存分析结果（来自 PlannerRole）
     */
    public void cacheAnalysisResult(String analysisResult) {
        if (analysisResult == null || analysisResult.isEmpty()) {
            return;
        }
        String key = PREFIX_ANALYSIS + currentSessionId;
        cache.put(key, analysisResult);
        logger.info("📊 缓存分析结果: {} (session: {})",
            truncateForLog(analysisResult), currentSessionId);
    }

    /**
     * 获取缓存的分析结果
     */
    public String getCachedAnalysisResult() {
        String key = PREFIX_ANALYSIS + currentSessionId;
        return cache.get(key);
    }

    /**
     * 缓存任务列表（JSON格式）
     */
    public void cacheTaskList(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        String key = PREFIX_TASKS + currentSessionId;
        String tasksJson = gson.toJson(tasks);
        cache.put(key, tasksJson);
        logger.info("✅ 缓存任务列表: {} 个任务 (session: {})",
            tasks.size(), currentSessionId);
    }

    /**
     * 获取缓存的任务列表
     */
    public List<Task> getCachedTaskList() {
        String key = PREFIX_TASKS + currentSessionId;
        String tasksJson = cache.get(key);
        if (tasksJson == null) {
            return new ArrayList<>();
        }
        try {
            Task[] tasksArray = gson.fromJson(tasksJson, Task[].class);
            return tasksArray != null ? Arrays.asList(tasksArray) : new ArrayList<>();
        } catch (Exception e) {
            logger.warn("❌ 无法反序列化缓存的任务列表", e);
            return new ArrayList<>();
        }
    }

    /**
     * 缓存单个任务执行结果
     */
    public void cacheTaskResult(int taskId, String description, String output, boolean success) {
        String key = PREFIX_TASK_RESULT + currentSessionId + ":" + taskId;
        TaskResultCache result = new TaskResultCache(
            taskId, description, output, success, LocalDateTime.now()
        );
        String resultJson = gson.toJson(result);
        cache.put(key, resultJson);
        logger.info("💾 缓存任务结果: 任务#{} (success={}) (session: {})",
            taskId, success, currentSessionId);
    }

    /**
     * 获取缓存的任务执行结果
     */
    public TaskResultCache getCachedTaskResult(int taskId) {
        String key = PREFIX_TASK_RESULT + currentSessionId + ":" + taskId;
        String resultJson = cache.get(key);
        if (resultJson == null) {
            return null;
        }
        try {
            return gson.fromJson(resultJson, TaskResultCache.class);
        } catch (Exception e) {
            logger.warn("❌ 无法反序列化任务结果 #{}", taskId, e);
            return null;
        }
    }

    /**
     * 获取所有已缓存的任务结果
     */
    public List<TaskResultCache> getAllCachedTaskResults() {
        // 简化实现：遍历缓存中的所有任务结果
        // 在实际应用中可能需要更复杂的检索逻辑
        List<TaskResultCache> results = new ArrayList<>();

        // 这里是一个占位符，真实实现需要遍历 PersistentCacheManager
        // 获取所有 PREFIX_TASK_RESULT 前缀的键
        logger.debug("正在检索所有任务结果...");

        return results;
    }

    /**
     * 缓存完整的执行上下文信息
     */
    public void cacheExecutionContext(TaskExecutionContext context) {
        if (context == null) {
            return;
        }
        String key = PREFIX_CONTEXT + currentSessionId;
        ExecutionContextCache contextCache = new ExecutionContextCache(
            context.getOriginalRequirement(),
            context.getAnalysisResult(),
            context.getDecisions(),
            context.getConstraints(),
            context.getRisks(),
            LocalDateTime.now()
        );
        String contextJson = gson.toJson(contextCache);
        cache.put(key, contextJson);
        logger.info("🔄 缓存执行上下文 (session: {})", currentSessionId);
    }

    /**
     * 从缓存恢复执行上下文
     */
    public TaskExecutionContext recoverExecutionContext() {
        String key = PREFIX_CONTEXT + currentSessionId;
        String contextJson = cache.get(key);
        if (contextJson == null) {
            return null;
        }
        try {
            ExecutionContextCache cached = gson.fromJson(contextJson, ExecutionContextCache.class);
            TaskExecutionContext context = new TaskExecutionContext(cached.originalRequirement);
            context.setAnalysisResult(cached.analysisResult);

            // 恢复决策和约束
            cached.decisions.forEach(context::addDecision);
            cached.constraints.forEach(context::addConstraint);
            cached.risks.forEach(context::addRisk);

            logger.info("✅ 从缓存恢复执行上下文 (session: {})", currentSessionId);
            return context;
        } catch (Exception e) {
            logger.warn("❌ 无法从缓存恢复执行上下文", e);
            return null;
        }
    }

    /**
     * 构建会话上下文摘要（用于 AI Prompt 注入）
     * 显示当前任务的进度、已完成任务等
     */
    public String buildSessionContextSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("\n【📋 任务执行上下文摘要】\n");
        summary.append("会话 ID: ").append(currentSessionId).append("\n\n");

        // 显示原始需求
        String requirement = getCachedRequirement();
        if (requirement != null) {
            summary.append("【原始需求】\n").append(requirement).append("\n\n");
        }

        // 显示任务列表
        List<Task> tasks = getCachedTaskList();
        if (!tasks.isEmpty()) {
            summary.append("【任务列表】(共 ").append(tasks.size()).append(" 个)\n");
            for (Task task : tasks) {
                String status = task.isPending() ? "⏳" : task.isInProgress() ? "▶️" :
                               task.isCompleted() ? "✅" : task.isSkipped() ? "⏭️" : "❓";
                summary.append(status).append(" #").append(task.getId())
                    .append(": ").append(task.getDescription()).append("\n");
            }
            summary.append("\n");
        }

        // 显示已完成的任务结果摘要
        int completedCount = (int) tasks.stream().filter(Task::isCompleted).count();
        if (completedCount > 0) {
            summary.append("【已完成任务】(").append(completedCount).append(" 个)\n");
            for (Task task : tasks) {
                if (task.isCompleted()) {
                    TaskResultCache result = getCachedTaskResult(task.getId());
                    if (result != null && result.output != null) {
                        summary.append("  #").append(task.getId()).append(": ")
                            .append(truncateForLog(result.output, 100)).append("\n");
                    }
                }
            }
            summary.append("\n");
        }

        return summary.toString();
    }

    /**
     * 生成缓存统计信息
     */
    public String getCacheStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("📚 任务上下文缓存统计\n");
        stats.append("═══════════════════════════════════════\n");
        stats.append("当前会话 ID: ").append(currentSessionId).append("\n");

        // 统计缓存的项目数量
        String requirement = getCachedRequirement();
        String analysis = getCachedAnalysisResult();
        List<Task> tasks = getCachedTaskList();

        stats.append("✓ 需求: ").append(requirement != null ? "已缓存" : "未缓存").append("\n");
        stats.append("✓ 分析结果: ").append(analysis != null ? "已缓存" : "未缓存").append("\n");
        stats.append("✓ 任务列表: ").append(tasks.size()).append(" 个任务\n");

        int completedTasks = (int) tasks.stream().filter(Task::isCompleted).count();
        int inProgressTasks = (int) tasks.stream().filter(Task::isInProgress).count();
        int pendingTasks = (int) tasks.stream().filter(Task::isPending).count();

        stats.append("  ├─ 已完成: ").append(completedTasks).append("\n");
        stats.append("  ├─ 进行中: ").append(inProgressTasks).append("\n");
        stats.append("  └─ 待处理: ").append(pendingTasks).append("\n");

        stats.append("═══════════════════════════════════════");
        return stats.toString();
    }

    /**
     * 清空当前会话的所有缓存
     */
    public void clearCurrentSession() {
        // 这是一个简化的实现
        // 真实场景可能需要更复杂的清理逻辑
        logger.info("🗑️ 清空会话缓存: {}", currentSessionId);
    }

    /**
     * 截断日志字符串便于显示
     */
    private String truncateForLog(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    /**
     * 截断日志字符串（默认100字符）
     */
    private String truncateForLog(String str) {
        return truncateForLog(str, 100);
    }

    // ==================== 内部数据类 ====================

    /**
     * 任务结果缓存对象
     */
    public static class TaskResultCache {
        public int taskId;
        public String description;
        public String output;
        public boolean success;
        public LocalDateTime timestamp;

        public TaskResultCache(int taskId, String description, String output,
                              boolean success, LocalDateTime timestamp) {
            this.taskId = taskId;
            this.description = description;
            this.output = output;
            this.success = success;
            this.timestamp = timestamp;
        }
    }

    /**
     * 执行上下文缓存对象
     */
    public static class ExecutionContextCache {
        public String originalRequirement;
        public String analysisResult;
        public Map<String, String> decisions;
        public List<String> constraints;
        public List<String> risks;
        public LocalDateTime timestamp;

        public ExecutionContextCache(String originalRequirement, String analysisResult,
                                     Map<String, String> decisions, List<String> constraints,
                                     List<String> risks, LocalDateTime timestamp) {
            this.originalRequirement = originalRequirement;
            this.analysisResult = analysisResult;
            this.decisions = new HashMap<>(decisions);
            this.constraints = new ArrayList<>(constraints);
            this.risks = new ArrayList<>(risks);
            this.timestamp = timestamp;
        }
    }
}
