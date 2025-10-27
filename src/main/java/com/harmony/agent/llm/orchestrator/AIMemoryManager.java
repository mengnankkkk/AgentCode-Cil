package com.harmony.agent.llm.orchestrator;

import com.harmony.agent.core.ai.PersistentCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 记忆管理器 - 使用 PersistentCacheManager 存储 AI 的长期记忆
 *
 * 记忆分类：
 * - file:* - 文件内容记忆
 * - search:* - 搜索结果记忆
 * - decision:* - 决策记录
 * - analysis:* - 分析结果
 */
public class AIMemoryManager {

    private static final Logger logger = LoggerFactory.getLogger(AIMemoryManager.class);

    private final PersistentCacheManager cache;

    public AIMemoryManager() {
        // 使用 "ai-memory" 作为缓存类型，启用持久化
        this.cache = new PersistentCacheManager("ai-memory", true);
        logger.info("AIMemoryManager initialized with persistent cache");
    }

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
     * 获取缓存统计信息
     * @return 缓存统计字符串
     */
    public String getCacheStats() {
        // TODO: 可以从 PersistentCacheManager 获取更详细的统计信息
        return "AI Memory Manager initialized with L1 (memory) + L2 (disk) caching";
    }
}
