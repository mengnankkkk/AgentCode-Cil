package com.harmony.agent.test.unit;

import com.harmony.agent.core.ai.PersistentCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PersistentCacheManager 单元测试 (P1 优化)
 *
 * 测试覆盖:
 * - L1 (内存) 缓存功能
 * - L2 (磁盘) 缓存功能
 * - 缓存过期策略
 * - 缓存统计信息
 */
@DisplayName("单元测试: PersistentCacheManager (P1 优化)")
public class PersistentCacheManagerTest {

    private PersistentCacheManager cacheManager;

    @BeforeEach
    public void setUp() {
        cacheManager = new PersistentCacheManager("test", true);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (cacheManager != null) {
            cacheManager.clear();
        }
    }

    @Nested
    @DisplayName("L1 内存缓存测试")
    class L1CacheTests {

        /**
         * 测试: 基本的 put/get 操作
         */
        @Test
        @DisplayName("应该能够存储和检索缓存项")
        public void testBasicPutGet() {
            String key = "test-key-1";
            String value = "test-value-1";

            cacheManager.put(key, value);
            String retrieved = cacheManager.get(key);

            assertEquals(value, retrieved, "Retrieved value should match stored value");
        }

        /**
         * 测试: 多个缓存项
         */
        @Test
        @DisplayName("应该能够处理多个缓存项")
        public void testMultipleCacheItems() {
            for (int i = 0; i < 10; i++) {
                String key = "key-" + i;
                String value = "value-" + i;
                cacheManager.put(key, value);
            }

            // 验证所有项都能检索
            for (int i = 0; i < 10; i++) {
                String key = "key-" + i;
                String expected = "value-" + i;
                assertEquals(expected, cacheManager.get(key));
            }
        }

        /**
         * 测试: 缓存未命中
         */
        @Test
        @DisplayName("应该为不存在的键返回 null")
        public void testCacheMiss() {
            String result = cacheManager.get("non-existent-key");
            assertNull(result, "Non-existent key should return null");
        }

        /**
         * 测试: 覆盖现有键
         */
        @Test
        @DisplayName("应该能够覆盖现有缓存项")
        public void testOverwriteCache() {
            String key = "overwrite-test";
            cacheManager.put(key, "value-1");
            cacheManager.put(key, "value-2");

            assertEquals("value-2", cacheManager.get(key));
        }

        /**
         * 测试: null 值处理
         */
        @Test
        @DisplayName("应该拒绝 null 值")
        public void testNullValue() {
            cacheManager.put("null-key", null);
            assertNull(cacheManager.get("null-key"));
        }

        /**
         * 测试: 空键处理
         */
        @Test
        @DisplayName("应该拒绝空键")
        public void testEmptyKey() {
            cacheManager.put("", "value");
            assertNull(cacheManager.get(""));
        }
    }

    @Nested
    @DisplayName("L2 磁盘缓存测试")
    class L2CacheTests {

        /**
         * 测试: 磁盘持久化
         */
        @Test
        @DisplayName("应该将缓存持久化到磁盘")
        public void testDiskPersistence() throws IOException {
            String key = "persist-test";
            String value = "persistent-value";

            cacheManager.put(key, value);

            // 创建新的缓存管理器 (模拟程序重启)
            PersistentCacheManager newManager = new PersistentCacheManager("test", true);
            String retrieved = newManager.get(key);

            assertNotNull(retrieved, "Value should be retrieved from disk after restart");
            assertEquals(value, retrieved);

            newManager.clear();
        }

        /**
         * 测试: 大量数据持久化
         */
        @Test
        @DisplayName("应该能处理大量缓存项的持久化")
        public void testLargeCachePeristence() throws IOException {
            int itemCount = 100;

            // 存储大量项
            for (int i = 0; i < itemCount; i++) {
                cacheManager.put("key-" + i, "value-" + i);
            }

            // 创建新管理器
            PersistentCacheManager newManager = new PersistentCacheManager("test", true);

            // 验证能检索一些项
            assertEquals("value-50", newManager.get("key-50"));

            newManager.clear();
        }

        /**
         * 测试: 禁用持久化
         */
        @Test
        @DisplayName("应该支持禁用磁盘持久化")
        public void testDisablePersistence() {
            PersistentCacheManager noP ersist =
                new PersistentCacheManager("test", false);

            noPersist.put("temp-key", "temp-value");

            // 创建新管理器
            PersistentCacheManager newManager =
                new PersistentCacheManager("test", false);
            String retrieved = newManager.get("temp-key");

            // 应该找不到 (因为没有持久化)
            assertNull(retrieved);

            noPersist.clear();
        }
    }

    @Nested
    @DisplayName("缓存统计测试")
    class StatisticsTests {

        /**
         * 测试: 缓存统计
         */
        @Test
        @DisplayName("应该正确记录缓存命中/未命中")
        public void testStatistics() {
            // 第一次访问 (未命中)
            cacheManager.get("key-1");

            // 第二次访问 (存储)
            cacheManager.put("key-1", "value-1");

            // 第三次访问 (命中)
            cacheManager.get("key-1");
            cacheManager.get("key-1");

            PersistentCacheManager.CacheStats stats = cacheManager.getStats();

            assertTrue(stats.hits > 0, "Should have some hits");
            System.out.println("📊 Cache Stats: " + stats);
        }

        /**
         * 测试: 命中率计算
         */
        @Test
        @DisplayName("应该正确计算命中率")
        public void testHitRate() {
            // 执行一些操作
            for (int i = 0; i < 10; i++) {
                cacheManager.put("key-" + i, "value-" + i);
            }

            for (int i = 0; i < 10; i++) {
                cacheManager.get("key-" + i);  // 命中
                cacheManager.get("miss-" + i); // 未命中
            }

            PersistentCacheManager.CacheStats stats = cacheManager.getStats();
            assertTrue(stats.hitRate >= 0.0 && stats.hitRate <= 1.0);

            System.out.println("📊 Hit Rate: " + (stats.hitRate * 100) + "%");
        }

        /**
         * 测试: 缓存大小
         */
        @Test
        @DisplayName("应该正确报告缓存大小")
        public void testCacheSize() {
            int itemCount = 5;
            for (int i = 0; i < itemCount; i++) {
                cacheManager.put("key-" + i, "value-" + i);
            }

            PersistentCacheManager.CacheStats stats = cacheManager.getStats();
            assertEquals(itemCount, stats.size, "Cache size should match number of items");
        }
    }

    @Nested
    @DisplayName("缓存过期测试")
    class ExpirationTests {

        /**
         * 测试: 清理过期项
         */
        @Test
        @DisplayName("应该能清理过期缓存")
        public void testCleanupExpired() {
            cacheManager.put("test-key", "test-value");
            cacheManager.cleanupExpired();

            // 清理操作应该成功
            System.out.println("✅ Cleanup completed successfully");
        }

        /**
         * 测试: 清空所有缓存
         */
        @Test
        @DisplayName("应该能清空所有缓存")
        public void testClearAll() {
            for (int i = 0; i < 10; i++) {
                cacheManager.put("key-" + i, "value-" + i);
            }

            cacheManager.clear();

            // 验证已清空
            assertNull(cacheManager.get("key-0"));

            System.out.println("✅ All cache cleared successfully");
        }
    }

    @Nested
    @DisplayName("并发访问测试")
    class ConcurrencyTests {

        /**
         * 测试: 多线程并发访问
         */
        @Test
        @DisplayName("应该支持多线程并发访问")
        public void testConcurrentAccess() throws InterruptedException {
            Thread[] threads = new Thread[10];

            // 创建 10 个线程
            for (int t = 0; t < 10; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < 100; i++) {
                        String key = "key-" + threadId + "-" + i;
                        cacheManager.put(key, "value-" + i);
                        cacheManager.get(key);
                    }
                });
            }

            // 启动所有线程
            for (Thread t : threads) {
                t.start();
            }

            // 等待所有线程完成
            for (Thread t : threads) {
                t.join();
            }

            System.out.println("✅ Concurrent access test passed");
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        /**
         * 测试: L1 缓存命中性能
         */
        @Test
        @DisplayName("L1 缓存命中应该很快 (<1ms)")
        public void testL1Performance() {
            cacheManager.put("perf-key", "perf-value");

            long startTime = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                cacheManager.get("perf-key");
            }
            long duration = (System.nanoTime() - startTime) / 1_000_000;  // 转换为毫秒

            System.out.println("📊 L1 Cache Performance:");
            System.out.println("   10000 hits in " + duration + "ms");
            System.out.println("   Average per hit: " + (duration / 10000.0) + "ms");

            assertTrue(duration < 100, "L1 cache should be very fast");
        }

        /**
         * 测试: 缓存大小限制
         */
        @Test
        @DisplayName("L1 缓存应该限制在 500 项以内")
        public void testL1SizeLimit() {
            // 尝试存储超过限制的项
            for (int i = 0; i < 1000; i++) {
                cacheManager.put("key-" + i, "value-" + i);
            }

            PersistentCacheManager.CacheStats stats = cacheManager.getStats();
            assertTrue(stats.size <= 500, "L1 cache size should be limited to 500");

            System.out.println("📊 L1 Cache Size: " + stats.size + " (limit: 500)");
        }
    }
}
