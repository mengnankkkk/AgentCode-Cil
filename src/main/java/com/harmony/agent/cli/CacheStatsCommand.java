package com.harmony.agent.cli;

import com.harmony.agent.core.ai.CachedAiValidationClient;
import com.harmony.agent.core.ai.PersistentCacheManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.logging.Logger;

/**
 * 显示 AI 验证缓存统计信息
 *
 * 使用场景:
 * - 了解缓存效率
 * - 查看节省的时间和成本
 * - 诊断缓存健康状况
 *
 * 用法:
 * java -jar harmony-safe-agent.jar cache-stats
 * java -jar harmony-safe-agent.jar cache-stats --cleanup
 * java -jar harmony-safe-agent.jar cache-stats --clear
 */
@Command(
    name = "cache-stats",
    description = "Display AI validation cache statistics and performance metrics",
    mixinStandardHelpOptions = true
)
public class CacheStatsCommand implements Runnable {

    private static final Logger logger = Logger.getLogger(CacheStatsCommand.class.getName());

    @Option(
        names = {"--cleanup"},
        description = "Clean up expired cache entries (7+ days old)",
        defaultValue = "false"
    )
    private boolean cleanup;

    @Option(
        names = {"--clear"},
        description = "Clear all cache entries",
        defaultValue = "false"
    )
    private boolean clear;

    @Option(
        names = {"--verbose", "-v"},
        description = "Show detailed statistics with time breakdown",
        defaultValue = "false"
    )
    private boolean verbose;

    @Override
    public void run() {
        try {
            PersistentCacheManager cacheManager = new PersistentCacheManager("p3", true);

            // 处理清理操作
            if (clear) {
                cacheManager.clear();
                System.out.println("\n✅ Cache cleared successfully\n");
                return;
            }

            if (cleanup) {
                cacheManager.cleanupExpired();
                System.out.println("\n✅ Expired cache cleaned up\n");
            }

            // 显示统计信息
            PersistentCacheManager.CacheStats stats = cacheManager.getStats();

            if (verbose) {
                System.out.println("\n" + stats.toDetailedString() + "\n");
            } else {
                printSummary(stats);
            }

        } catch (Exception e) {
            System.err.println("❌ Error reading cache stats: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }

    private void printSummary(PersistentCacheManager.CacheStats stats) {
        System.out.println();
        System.out.println("┌────────────────────────────────────────┐");
        System.out.println("│     📊 Cache Statistics Summary        │");
        System.out.println("├────────────────────────────────────────┤");
        System.out.printf("│ Cache Hits:      %,10d           │%n", stats.hits);
        System.out.printf("│ Cache Misses:    %,10d           │%n", stats.misses);
        System.out.printf("│ Hit Rate:        %,9.1f%%          │%n", stats.hitRate * 100);
        System.out.printf("│ Cached Items:    %,10d           │%n", stats.size);
        System.out.printf("│ Time Saved:      ~%-9d seconds  │%n", stats.hits * 1500 / 1000);
        System.out.println("└────────────────────────────────────────┘");
        System.out.println();

        // 显示优化建议
        if (stats.hits == 0) {
            System.out.println("💡 Tip: No cache hits yet. Run analysis multiple times to see cache benefits!");
        } else if (stats.hitRate < 0.5) {
            System.out.println("💡 Tip: Cache hit rate is below 50%. Consider analyzing similar code patterns.");
        } else {
            System.out.println("✨ Great! You're getting good cache hit rates. Keep running similar analyses!");
        }
        System.out.println();
    }
}
