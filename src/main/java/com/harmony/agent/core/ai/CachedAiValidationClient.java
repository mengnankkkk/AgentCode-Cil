package com.harmony.agent.core.ai;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Cached AI Validation Client - Decorator pattern with Persistent cache
 * Supports both in-memory (L1) and disk-based (L2) caching
 * Caches AI validation responses to reduce API costs and latency
 *
 * Performance:
 * - L1 Cache Hit: <1ms
 * - L2 Cache Hit: ~5ms
 * - Cache Miss: 1500ms (actual API call)
 */
public class CachedAiValidationClient {

    private static final Logger logger = LoggerFactory.getLogger(CachedAiValidationClient.class);

    private final AiValidationClient delegate;
    private final PersistentCacheManager persistentCache;
    private final Cache<String, String> legacyCache;  // 保持向后兼容

    // 缓存使用选项
    private final boolean usePersistentCache;
    private boolean cacheEnabled = true;

    /**
     * 使用持久化缓存的构造函数（推荐）
     *
     * @param delegate 底层 AI 验证客户端
     */
    public CachedAiValidationClient(AiValidationClient delegate) {
        this.delegate = delegate;
        this.usePersistentCache = true;
        this.persistentCache = new PersistentCacheManager("p3", true);

        // 保留空 legacyCache 以保持向后兼容
        this.legacyCache = CacheBuilder.newBuilder()
            .maximumSize(10)  // 最小值，几乎不用
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

        logger.info("Cached AI client initialized with Persistent Cache (P1 Optimization)");
    }

    /**
     * 使用传统 Guava 缓存的构造函数（已弃用，仅用于向后兼容）
     *
     * @param delegate 底层 AI 验证客户端
     * @param maxSize 最大缓存条目数
     * @param ttlHours 缓存 TTL（小时）
     */
    @Deprecated
    public CachedAiValidationClient(AiValidationClient delegate, int maxSize, int ttlHours) {
        this.delegate = delegate;
        this.usePersistentCache = false;
        this.persistentCache = null;

        this.legacyCache = CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttlHours, TimeUnit.HOURS)
            .recordStats()
            .build();

        logger.warn("Using legacy in-memory-only cache. Consider using PersistentCacheManager for better performance.");
        logger.info("Cached AI client initialized with Legacy Cache (maxSize: {}, TTL: {}h)", maxSize, ttlHours);
    }

    /**
     * 发送验证请求并使用缓存
     *
     * @param prompt 验证提示
     * @param expectJson 是否期望 JSON 响应
     * @return LLM 响应内容（来自缓存或新鲜）
     * @throws AiValidationClient.AiClientException 如果请求失败
     */
    public String sendRequest(String prompt, boolean expectJson)
            throws AiValidationClient.AiClientException {

        if (!cacheEnabled) {
            return delegate.sendRequest(prompt, expectJson);
        }

        String cacheKey = createCacheKey(prompt, expectJson);

        if (usePersistentCache) {
            // 使用新的持久化缓存 (P1 优化)
            String cached = persistentCache.get(cacheKey);
            if (cached != null) {
                logger.debug("Cache HIT (persistent) - returning cached response");
                return cached;
            }

            logger.debug("Cache MISS - sending request to LLM");
            String result = delegate.sendRequest(prompt, expectJson);
            persistentCache.put(cacheKey, result);
            return result;

        } else {
            // 使用传统 Guava 缓存（向后兼容）
            try {
                String result = legacyCache.get(cacheKey, () -> {
                    logger.debug("Cache MISS - sending request to LLM");
                    return delegate.sendRequest(prompt, expectJson);
                });

                logger.debug("Cache HIT (legacy) - returning cached response");
                return result;

            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AiValidationClient.AiClientException) {
                    throw (AiValidationClient.AiClientException) cause;
                } else {
                    throw new AiValidationClient.AiClientException(
                        "Failed to retrieve from cache or delegate", cause);
                }
            }
        }
    }

    /**
     * 创建缓存键
     * 使用哈希值以节省长提示的内存
     */
    private String createCacheKey(String prompt, boolean expectJson) {
        // 使用哈希节省内存（提示可能很长）
        int promptHash = prompt.hashCode();
        return promptHash + "#" + (expectJson ? "json" : "text");
    }

    /**
     * 检查客户端是否可用
     */
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    /**
     * 获取提供商名称
     */
    public String getProviderName() {
        return delegate.getProviderName();
    }

    /**
     * 获取模型名称
     */
    public String getModelName() {
        return delegate.getModelName();
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        if (usePersistentCache && persistentCache != null) {
            PersistentCacheManager.CacheStats pStats = persistentCache.getStats();
            return new CacheStats(pStats.hits, pStats.misses, pStats.size, pStats.hitRate);
        } else {
            com.google.common.cache.CacheStats stats = legacyCache.stats();
            return new CacheStats(
                (long) stats.hitCount(),
                (long) stats.missCount(),
                legacyCache.size(),
                stats.hitRate()
            );
        }
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        if (usePersistentCache && persistentCache != null) {
            persistentCache.clear();
            logger.info("Persistent cache cleared");
        } else {
            legacyCache.invalidateAll();
            logger.info("Legacy cache cleared");
        }
    }

    /**
     * 启用/禁用缓存
     */
    public void setCacheEnabled(boolean enabled) {
        this.cacheEnabled = enabled;
        logger.info("Cache {}", enabled ? "enabled" : "disabled");
    }

    /**
     * 清理过期缓存（仅持久化缓存）
     */
    public void cleanupExpired() {
        if (usePersistentCache && persistentCache != null) {
            persistentCache.cleanupExpired();
            logger.info("Expired persistent cache cleaned up");
        }
    }

    /**
     * 缓存统计信息持有类
     */
    public static class CacheStats {
        private final long hits;
        private final long misses;
        private final long size;
        private final double hitRate;

        public CacheStats(long hits, long misses, long size, double hitRate) {
            this.hits = hits;
            this.misses = misses;
            this.size = size;
            this.hitRate = hitRate;
        }

        public long getHits() {
            return hits;
        }

        public long getMisses() {
            return misses;
        }

        public long getSize() {
            return size;
        }

        public double getHitRate() {
            return hitRate;
        }

        public double getCostSavings() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }

        public long getTimeSaved() {
            return hits * 1500;  // 每个缓存命中节省 1.5 秒
        }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{hits=%,d, misses=%,d, size=%,d, hitRate=%.1f%%, savings=%.1f%%, timeSaved=~%,d sec}",
                hits, misses, size, hitRate * 100, getCostSavings() * 100, getTimeSaved() / 1000
            );
        }

        public String toDetailedString() {
            long total = hits + misses;
            long timeSaved = getTimeSaved();

            return String.format(
                "╔════════════════════════════════════════╗%n" +
                "║       📊 AI Validation Cache Stats     ║%n" +
                "╠════════════════════════════════════════╣%n" +
                "║  Total Hits:      %,15d     ║%n" +
                "║  Total Misses:    %,15d     ║%n" +
                "║  Total Requests:  %,15d     ║%n" +
                "║  Cache Size:      %,15d items║%n" +
                "║  Hit Rate:        %,14.1f%%   ║%n" +
                "║  Cost Savings:    %,14.1f%%   ║%n" +
                "║  Time Saved:      ~%-13d sec║%n" +
                "╚════════════════════════════════════════╝",
                hits, misses, total, size, hitRate * 100, getCostSavings() * 100, timeSaved / 1000
            );
        }
    }
}