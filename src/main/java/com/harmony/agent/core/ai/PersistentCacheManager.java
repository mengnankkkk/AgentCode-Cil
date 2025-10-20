package com.harmony.agent.core.ai;

import com.google.common.cache.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 分层缓存管理器: L1 内存缓存 + L2 磁盘缓存
 *
 * 使用场景:
 * - P2 静态分析结果缓存 (文件内容 SHA256 → 问题列表)
 * - P3 AI 验证结果缓存 (问题特征 SHA256 → AI 决策)
 *
 * 性能特点:
 * - L1 (内存): <1ms, 500条记录, 1小时TTL
 * - L2 (磁盘): ~5ms, 无限条记录, 7天TTL
 *
 * @author HarmonyAgent
 * @version 1.0
 */
public class PersistentCacheManager {

    private static final Logger logger = Logger.getLogger(PersistentCacheManager.class.getName());

    private static final String CACHE_DIR =
        System.getProperty("user.home") + "/.harmony_agent/cache";
    private static final int L1_SIZE = 500;
    private static final int L1_TTL_HOURS = 1;
    private static final int L2_TTL_DAYS = 7;

    private final Cache<String, String> l1Cache;
    private final Path l2CachePath;
    private final String cacheType;  // "p2" 或 "p3"
    private final boolean persistent;

    /**
     * 创建缓存管理器
     *
     * @param cacheType "p2" (静态分析) 或 "p3" (AI验证)
     * @param persistent 是否启用磁盘持久化
     */
    public PersistentCacheManager(String cacheType, boolean persistent) {
        this.cacheType = cacheType;
        this.persistent = persistent;
        this.l2CachePath = Paths.get(CACHE_DIR, cacheType);

        // 初始化 L1 缓存 (Guava)
        this.l1Cache = CacheBuilder.newBuilder()
            .maximumSize(L1_SIZE)
            .expireAfterWrite(L1_TTL_HOURS, TimeUnit.HOURS)
            .recordStats()
            .build();

        // 创建磁盘缓存目录
        if (persistent) {
            try {
                Files.createDirectories(l2CachePath);
                logger.info("Persistent cache directory created: " + l2CachePath);
            } catch (IOException e) {
                logger.warning("Failed to create cache directory: " + l2CachePath);
                throw new RuntimeException("Failed to create cache directory", e);
            }
        }
    }

    /**
     * 兼容构造函数（默认启用持久化）
     */
    public PersistentCacheManager(String cacheType) {
        this(cacheType, true);
    }

    /**
     * 获取缓存值 (L1 → L2)
     *
     * @param key 缓存键
     * @return 缓存值，如果未找到返回 null
     */
    public String get(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        // 第1步：检查 L1 内存缓存
        String cached = l1Cache.getIfPresent(key);
        if (cached != null) {
            logger.fine("Cache L1 HIT: " + shortKey(key));
            return cached;
        }

        // 第2步：检查 L2 磁盘缓存（如果启用）
        if (!persistent) {
            logger.fine("Cache L1 MISS (persistent disabled): " + shortKey(key));
            return null;
        }

        Path cacheFile = getCacheFile(key);
        if (Files.exists(cacheFile)) {
            try {
                // 检查过期时间
                if (!isExpired(cacheFile)) {
                    cached = Files.readString(cacheFile);

                    // 回源到 L1（热数据）
                    l1Cache.put(key, cached);
                    logger.fine("Cache L2 HIT (promoted to L1): " + shortKey(key));
                    return cached;
                } else {
                    // 删除过期缓存
                    try {
                        Files.delete(cacheFile);
                        logger.fine("Expired cache deleted: " + shortKey(key));
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException e) {
                logger.warning("Failed to read cache file: " + cacheFile);
            }
        }

        logger.fine("Cache MISS: " + shortKey(key));
        return null;
    }

    /**
     * 存储缓存值 (L1 + L2)
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    public void put(String key, String value) {
        if (key == null || key.isEmpty() || value == null) {
            return;
        }

        // 写入 L1
        l1Cache.put(key, value);
        logger.fine("Cached to L1: " + shortKey(key));

        // 写入 L2（如果启用）
        if (!persistent) {
            return;
        }

        Path cacheFile = getCacheFile(key);
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, value);
            logger.fine("Cached to L2: " + shortKey(key));
        } catch (IOException e) {
            logger.warning("Failed to persist cache to disk: " + cacheFile);
            // 继续执行，只是丢失磁盘缓存
        }
    }

    /**
     * 清理所有过期缓存
     */
    public void cleanupExpired() {
        if (!persistent) {
            return;
        }

        try {
            Files.list(l2CachePath)
                .forEach(path -> {
                    if (isExpired(path)) {
                        try {
                            Files.delete(path);
                            logger.fine("Cleaned expired cache: " + path.getFileName());
                        } catch (IOException ignored) {
                        }
                    }
                });
            logger.info("Cache cleanup completed");
        } catch (IOException e) {
            logger.warning("Failed to cleanup cache: " + e.getMessage());
        }
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        l1Cache.invalidateAll();
        logger.info("L1 cache cleared");

        if (!persistent) {
            return;
        }

        try {
            Files.list(l2CachePath)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                    }
                });
            logger.info("L2 cache cleared");
        } catch (IOException e) {
            logger.warning("Failed to clear L2 cache: " + e.getMessage());
        }
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        com.google.common.cache.CacheStats l1Stats = l1Cache.stats();

        int l2Count = 0;
        if (persistent) {
            try {
                l2Count = (int) Files.list(l2CachePath).count();
            } catch (IOException ignored) {
            }
        }

        return new CacheStats(
            (long) l1Stats.hitCount(),
            (long) l1Stats.missCount(),
            (int) l1Cache.size() + l2Count,
            l1Stats.hitRate()
        );
    }

    /**
     * 获取缓存文件路径
     */
    private Path getCacheFile(String key) {
        // 使用哈希作为文件名（避免路径过长）
        String hashedKey = Integer.toHexString(Math.abs(key.hashCode()));
        return l2CachePath.resolve(hashedKey + ".cache");
    }

    /**
     * 检查文件是否过期
     */
    private boolean isExpired(Path cacheFile) {
        try {
            long lastModified = Files.getLastModifiedTime(cacheFile).toMillis();
            long ageMillis = System.currentTimeMillis() - lastModified;
            long ttlMillis = L2_TTL_DAYS * 24 * 60 * 60 * 1000L;
            return ageMillis > ttlMillis;
        } catch (IOException e) {
            return true;  // 出错则认为过期
        }
    }

    /**
     * 缩短密钥显示
     */
    private String shortKey(String key) {
        if (key.length() <= 20) return key;
        return key.substring(0, 20) + "...";
    }

    /**
     * 缓存统计数据类
     */
    public static class CacheStats {
        public final long hits;
        public final long misses;
        public final int size;
        public final double hitRate;

        public CacheStats(long hits, long misses, int size, double hitRate) {
            this.hits = hits;
            this.misses = misses;
            this.size = size;
            this.hitRate = hitRate;
        }

        @Override
        public String toString() {
            long total = hits + misses;
            return String.format(
                "CacheStats{hits=%,d, misses=%,d, total=%,d, size=%d, hitRate=%.1f%%}",
                hits, misses, total, size, hitRate * 100
            );
        }

        public String toDetailedString() {
            long total = hits + misses;
            long timeSaved = hits * 1500;  // 每个缓存命中节省 1.5s

            return String.format(
                "╔════════════════════════════════════════╗\n" +
                "║  📊 Cache Statistics                   ║\n" +
                "╠════════════════════════════════════════╣\n" +
                "║  Hits:        %,10d            ║\n" +
                "║  Misses:      %,10d            ║\n" +
                "║  Total:       %,10d            ║\n" +
                "║  Size:        %,10d items       ║\n" +
                "║  Hit Rate:    %,10.1f%%        ║\n" +
                "║  Time Saved:  ~%-10d seconds  ║\n" +
                "╚════════════════════════════════════════╝",
                hits, misses, total, size, hitRate * 100, timeSaved / 1000
            );
        }
    }
}
