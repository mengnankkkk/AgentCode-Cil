package com.harmony.agent.llm.orchestrator;

import com.harmony.agent.core.ai.PersistentCacheManager;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.store.UnifiedIssueStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * AI 记忆管理器 - 使用 PersistentCacheManager 存储 AI 的长期记忆
 *
 * 记忆分类（优先级递减）：
 * - issue:* - 代码问题记忆（最关键，作为AI的主要知识库）
 * - file:* - 文件内容记忆
 * - search:* - 搜索结果记忆
 * - decision:* - 决策记录
 * - analysis:* - 分析结果
 * - tool:* - 工具执行结果
 */
public class AIMemoryManager {

    private static final Logger logger = LoggerFactory.getLogger(AIMemoryManager.class);

    private final PersistentCacheManager cache;
    private final UnifiedIssueStore issueStore;
    private final Gson gson;

    public AIMemoryManager() {
        // 使用 "ai-memory" 作为缓存类型，启用持久化
        this.cache = new PersistentCacheManager("ai-memory", true);
        this.issueStore = new UnifiedIssueStore();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        logger.info("AIMemoryManager initialized with persistent cache + issue memory");
    }

    /**
     * 获取内部的 UnifiedIssueStore
     * 用于其他模块与AI记忆共享问题数据
     */
    public UnifiedIssueStore getIssueStore() {
        return issueStore;
    }

    // ====== 问题记忆（Issue Memory）- 最高优先级 ======

    /**
     * 记住单个问题（自动存储到缓存）
     * @param issue 安全问题
     */
    public void rememberIssue(SecurityIssue issue) {
        if (issue == null) {
            return;
        }

        // 添加到 UnifiedIssueStore
        issueStore.addIssue(issue);

        // 同时存储到缓存（JSON格式），方便AI检索
        String cacheKey = "issue:" + issue.getHash();
        String issueJson = gson.toJson(issue);
        cache.put(cacheKey, issueJson);

        logger.info("🔴 Remembered issue: {} [{}] at {}:{}",
            issue.getTitle(),
            issue.getSeverity().getDisplayName(),
            issue.getLocation().getFilePath(),
            issue.getLocation().getLineNumber());
    }

    /**
     * 记住多个问题（批量存储）
     * @param issues 安全问题集合
     */
    public void rememberIssues(Collection<SecurityIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }

        for (SecurityIssue issue : issues) {
            rememberIssue(issue);
        }
    }

    /**
     * 获取已记住的问题（从UnifiedIssueStore）
     * @return 所有已记住的问题
     */
    public List<SecurityIssue> getRememberedIssues() {
        return issueStore.getAllIssues();
    }

    /**
     * 获取特定文件的已知问题
     * @param filePath 文件路径
     * @return 该文件的所有已知问题
     */
    public List<SecurityIssue> getIssuesForFile(String filePath) {
        return issueStore.getIssuesByFile(filePath);
    }

    /**
     * 获取特定严重级别的问题
     * @param severity 严重级别
     * @return 该级别的所有问题
     */
    public List<SecurityIssue> getIssuesBySeverity(IssueSeverity severity) {
        return issueStore.getIssuesBySeverity(severity);
    }

    /**
     * 构建问题上下文，用于Prompt注入
     * 让AI了解当前已知的问题（最多显示最严重的5个）
     * @return 格式化的问题上下文
     */
    public String buildIssueContext() {
        List<SecurityIssue> allIssues = issueStore.getAllIssues();

        if (allIssues.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n【🔴 已知问题库 - AI记忆】\n");
        context.append("系统已发现以下代码问题（优先关注严重问题）：\n\n");

        // 按严重级别排序，显示最严重的问题
        allIssues.stream()
            .sorted((a, b) -> Integer.compare(
                b.getSeverity().getLevel(),
                a.getSeverity().getLevel()))
            .limit(5)
            .forEach(issue -> {
                context.append(String.format(
                    "【%s】%s\n" +
                    "  位置：%s:%d\n" +
                    "  描述：%s\n\n",
                    issue.getSeverity().getDisplayName(),
                    issue.getTitle(),
                    issue.getLocation().getFilePath(),
                    issue.getLocation().getLineNumber(),
                    issue.getDescription()
                ));
            });

        // 统计信息
        Map<IssueSeverity, Long> bySeverity = issueStore.countBySeverity();
        context.append("【统计】总问题数：")
            .append(allIssues.size())
            .append("，其中 严重:")
            .append(bySeverity.getOrDefault(IssueSeverity.CRITICAL, 0L))
            .append(" 高:")
            .append(bySeverity.getOrDefault(IssueSeverity.HIGH, 0L))
            .append(" 中:")
            .append(bySeverity.getOrDefault(IssueSeverity.MEDIUM, 0L))
            .append("\n");

        return context.toString();
    }

    /**
     * 清空所有问题记忆（重置会话时调用）
     */
    public void clearIssueMemory() {
        issueStore.clear();
        logger.info("✨ Issue memory cleared");
    }

    /**
     * 获取问题记忆统计
     * @return 统计字符串
     */
    public String getIssueMemoryStats() {
        return issueStore.getStatistics();
    }

    // ====== 文件记忆（File Memory）======

    /**
     * 存储文件内容到记忆
     * @param filePath 文件路径
     * @param content 文件内容
     */
    public void rememberFile(String filePath, String content) {
        if (filePath == null || content == null) {
            return;
        }
        String key = "file:" + filePath;
        cache.put(key, content);
        logger.info("📝 Remembered file: {} ({} chars)", filePath, content.length());
    }

    /**
     * 存储搜索结果到记忆
     * @param keyword 搜索关键词
     * @param results 搜索结果
     */
    public void rememberSearchResult(String keyword, String results) {
        if (keyword == null || results == null) {
            return;
        }
        String key = "search:" + keyword;
        cache.put(key, results);
        logger.info("🔍 Remembered search results for: {}", keyword);
    }

    /**
     * 存储分析结果到记忆
     * @param analysisId 分析 ID
     * @param result 分析结果
     */
    public void rememberAnalysis(String analysisId, String result) {
        if (analysisId == null || result == null) {
            return;
        }
        String key = "analysis:" + analysisId;
        cache.put(key, result);
        logger.info("📊 Remembered analysis: {}", analysisId);
    }

    /**
     * 存储决策记录到记忆
     * @param decisionId 决策 ID
     * @param decision 决策内容
     */
    public void rememberDecision(String decisionId, String decision) {
        if (decisionId == null || decision == null) {
            return;
        }
        String key = "decision:" + decisionId;
        cache.put(key, decision);
        logger.info("💡 Remembered decision: {}", decisionId);
    }

    /**
     * 存储工具执行结果到记忆
     * @param toolName 工具名称
     * @param result 执行结果
     */
    public void rememberToolResult(String toolName, String result) {
        if (toolName == null || result == null) {
            return;
        }
        String key = "tool:" + toolName + ":" + System.currentTimeMillis();
        cache.put(key, result);
        logger.info("🔧 Remembered tool result: {}", toolName);
    }

    /**
     * 获取文件记忆
     * @param filePath 文件路径
     * @return 文件内容，如果不存在返回 null
     */
    public String getFileMemory(String filePath) {
        if (filePath == null) {
            return null;
        }
        String key = "file:" + filePath;
        return cache.get(key);
    }

    /**
     * 获取搜索结果记忆
     * @param keyword 搜索关键词
     * @return 搜索结果，如果不存在返回 null
     */
    public String getSearchMemory(String keyword) {
        if (keyword == null) {
            return null;
        }
        String key = "search:" + keyword;
        return cache.get(key);
    }

    /**
     * 获取任意记忆
     * @param key 记忆键（例如 "file:App.java", "search:TODO"）
     * @return 记忆内容，如果不存在返回 null
     */
    public String getMemory(String key) {
        if (key == null) {
            return null;
        }
        return cache.get(key);
    }

    /**
     * 构建上下文信息用于 Prompt 注入
     * 根据查询关键词检索相关记忆
     * @param query 查询关键词
     * @return 格式化的记忆信息，可以注入到 Prompt
     */
    public String buildMemoryContext(String query) {
        StringBuilder context = new StringBuilder();

        // 尝试查找相关的文件记忆
        if (query != null && !query.isEmpty()) {
            // 这里可以实现更复杂的记忆检索逻辑
            // 目前简化为直接查询关键词匹配的记忆
            String fileMemory = getSearchMemory(query);
            if (fileMemory != null && !fileMemory.isEmpty()) {
                context.append("\n【相关记忆 - 搜索结果】\n");
                context.append(fileMemory);
                context.append("\n");
            }
        }

        return context.length() > 0 ? context.toString() : "";
    }

    /**
     * 清理过期的记忆
     * PersistentCacheManager 会自动清理 7 天前的数据
     */
    public void cleanupExpired() {
        cache.cleanupExpired();
        logger.info("✨ Expired memories cleaned");
    }

    /**
     * 获取缓存统计信息（包括问题记忆统计）
     * @return 缓存统计字符串
     */
    public String getCacheStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("📚 AI 记忆统计信息\n");
        stats.append("═══════════════════════════════════════\n");
        stats.append("【缓存层】L1 (内存) + L2 (磁盘) 二层缓存\n");
        stats.append("【问题库】").append(issueStore.getTotalIssueCount())
            .append(" 个已知问题\n");
        stats.append("  ├─ 文件数：").append(issueStore.getAllIssues().stream()
            .map(issue -> issue.getLocation().getFilePath())
            .distinct()
            .count()).append("\n");

        Map<IssueSeverity, Long> bySeverity = issueStore.countBySeverity();
        stats.append("  ├─ 严重：").append(bySeverity.getOrDefault(IssueSeverity.CRITICAL, 0L)).append("\n");
        stats.append("  ├─ 高：").append(bySeverity.getOrDefault(IssueSeverity.HIGH, 0L)).append("\n");
        stats.append("  ├─ 中：").append(bySeverity.getOrDefault(IssueSeverity.MEDIUM, 0L)).append("\n");
        stats.append("  └─ 低/信息：")
            .append(bySeverity.getOrDefault(IssueSeverity.LOW, 0L) +
                   bySeverity.getOrDefault(IssueSeverity.INFO, 0L)).append("\n");

        stats.append("═══════════════════════════════════════");
        return stats.toString();
    }
}
