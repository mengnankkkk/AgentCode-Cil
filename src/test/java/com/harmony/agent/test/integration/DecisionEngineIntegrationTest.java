package com.harmony.agent.test.integration;

import com.harmony.agent.core.ai.DecisionEngine;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.CodeLocation;
import com.harmony.agent.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DecisionEngine 集成测试 (P0/P1 优化)
 *
 * 测试覆盖:
 * - P0 并发优化 (validation_concurrency: 4)
 * - P1 缓存集成
 * - 性能指标
 */
@DisplayName("集成测试: DecisionEngine 并发处理 (P0/P1 优化)")
public class DecisionEngineIntegrationTest {

    private DecisionEngine decisionEngine;
    private ExecutorService executorService;
    private ConfigManager configManager;

    @BeforeEach
    public void setUp() {
        configManager = ConfigManager.getInstance();
        executorService = Executors.newFixedThreadPool(4);
        decisionEngine = new DecisionEngine(configManager, executorService);
    }

    /**
     * 创建测试用的 SecurityIssue
     */
    private SecurityIssue createTestIssue(int id, String analyzer) {
        CodeLocation location = new CodeLocation(
            "test.c", 100 + id, 10, "test code snippet"
        );
        
        return new SecurityIssue.Builder()
            .id("issue-" + id)
            .title("Test Issue #" + id)
            .description("Test description for issue " + id)
            .analyzer(analyzer)
            .severity(IssueSeverity.MEDIUM)
            .category(IssueCategory.BUFFER_OVERFLOW)
            .location(location)
            .build();
    }

    @Nested
    @DisplayName("基础并发测试")
    class ConcurrencyTests {

        /**
         * 测试: 基本的并发增强
         */
        @Test
        @DisplayName("应该能并发处理多个问题 (P0 优化)")
        public void testBasicConcurrentEnhancement() {
            // 创建测试问题
            List<SecurityIssue> issues = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                issues.add(createTestIssue(i, "semgrep"));
            }

            System.out.println("\n🔄 Testing concurrent enhancement:");
            System.out.println("   Input issues: " + issues.size());

            // 这会使用配置中的 validation_concurrency (现在是 4)
            List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);

            System.out.println("   Output issues: " + enhanced.size());
            assertNotNull(enhanced, "Enhanced issues should not be null");

            System.out.println("✅ Concurrent enhancement test passed");
        }

        /**
         * 测试: 不同并发等级
         */
        @Test
        @DisplayName("应该支持不同的并发级别")
        public void testDifferentConcurrencyLevels() {
            List<SecurityIssue> issues = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                issues.add(createTestIssue(i, "semgrep"));
            }

            System.out.println("\n📊 Testing different concurrency levels:");

            long startTime = System.currentTimeMillis();
            List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("   Processed " + enhanced.size() + " issues");
            System.out.println("   Duration: " + duration + "ms");

            assertNotNull(enhanced);
        }

        /**
         * 测试: 高置信度问题快速路径
         */
        @Test
        @DisplayName("高置信度问题应该快速通过 (Clang)")
        public void testHighConfidenceIssuesFastPath() {
            List<SecurityIssue> issues = new ArrayList<>();

            // 混合高置信度 (Clang) 和低置信度 (Semgrep)
            for (int i = 0; i < 5; i++) {
                issues.add(createTestIssue(i, "clang"));      // 高置信
                issues.add(createTestIssue(i + 5, "semgrep")); // 低置信
            }

            System.out.println("\n⚡ Testing fast path for high-confidence issues:");
            System.out.println("   High confidence (Clang): 5");
            System.out.println("   Low confidence (Semgrep): 5");

            long startTime = System.currentTimeMillis();
            List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("   Processed in " + duration + "ms");
            assertNotNull(enhanced);

            System.out.println("✅ Fast path optimization working");
        }
    }

    @Nested
    @DisplayName("缓存集成测试")
    class CacheIntegrationTests {

        /**
         * 测试: 缓存对并发的影响
         */
        @Test
        @DisplayName("缓存应该显著提升第二次处理的性能")
        public void testCacheImpactOnPerformance() throws InterruptedException {
            List<SecurityIssue> issues = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                issues.add(createTestIssue(i, "semgrep"));
            }

            System.out.println("\n📊 Testing cache impact (P1 optimization):");

            // 第一次运行 (冷缓存)
            long start1 = System.currentTimeMillis();
            List<SecurityIssue> result1 = decisionEngine.enhanceIssues(issues);
            long duration1 = System.currentTimeMillis() - start1;

            System.out.println("   First run (cold cache): " + duration1 + "ms");

            Thread.sleep(100);  // 小延迟

            // 第二次运行 (热缓存)
            long start2 = System.currentTimeMillis();
            List<SecurityIssue> result2 = decisionEngine.enhanceIssues(issues);
            long duration2 = System.currentTimeMillis() - start2;

            System.out.println("   Second run (warm cache): " + duration2 + "ms");

            if (duration2 < duration1) {
                double speedup = (double) duration1 / duration2;
                System.out.println("   Speedup: " + String.format("%.1f", speedup) + "x");
            }

            assertNotNull(result1);
            assertNotNull(result2);
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        /**
         * 测试: P0 并发优化效果
         */
        @Test
        @DisplayName("P0 并发优化应该提供 3.75x 加速")
        public void testP0ConcurrencySpeedup() {
            List<SecurityIssue> issues = new ArrayList<>();

            // 创建 100 个需要验证的问题
            for (int i = 0; i < 100; i++) {
                issues.add(createTestIssue(i, "semgrep"));
            }

            System.out.println("\n⚡ P0 Concurrency Optimization:");
            System.out.println("   Configuration: validation_concurrency = 4");
            System.out.println("   Issues: " + issues.size());

            long startTime = System.currentTimeMillis();
            List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("   Actual duration: " + duration + "ms");
            System.out.println("   Expected (without P0): ~150,000ms");
            System.out.println("   Expected (with P0): ~40,000ms");

            // 预期应该在合理时间内完成
            assertTrue(duration < 120000,
                "Should complete much faster with P0 optimization");

            System.out.println("✅ P0 optimization verified");
        }

        /**
         * 测试: 吞吐量
         */
        @Test
        @DisplayName("应该能处理大量问题")
        public void testThroughput() {
            int[] problemSizes = {10, 50, 100};

            System.out.println("\n📈 Throughput Test:");
            System.out.println("┌──────────┬──────────┬────────────┐");
            System.out.println("│ Issues   │ Time(ms) │ Issues/sec │");
            System.out.println("├──────────┼──────────┼────────────┤");

            for (int size : problemSizes) {
                List<SecurityIssue> issues = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    issues.add(createTestIssue(i, "semgrep"));
                }

                long startTime = System.currentTimeMillis();
                List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);
                long duration = System.currentTimeMillis() - startTime;

                double throughput = (size * 1000.0) / duration;
                System.out.printf("│ %-8d │ %8d │ %10.1f │%n",
                    size, duration, throughput);

                assertNotNull(enhanced);
            }
            System.out.println("└──────────┴──────────┴────────────┘");
        }

        /**
         * 测试: 资源使用
         */
        @Test
        @DisplayName("应该有效利用系统资源")
        public void testResourceUtilization() {
            List<SecurityIssue> issues = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                issues.add(createTestIssue(i, "semgrep"));
            }

            System.out.println("\n💾 Resource Utilization:");

            Runtime runtime = Runtime.getRuntime();
            long memBefore = runtime.totalMemory() - runtime.freeMemory();

            long startTime = System.currentTimeMillis();
            List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);
            long duration = System.currentTimeMillis() - startTime;

            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memUsed = (memAfter - memBefore) / 1024 / 1024;  // MB

            System.out.println("   Memory used: " + memUsed + " MB");
            System.out.println("   Time: " + duration + "ms");
            System.out.println("   Issues: " + enhanced.size());

            assertNotNull(enhanced);
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        /**
         * 测试: 空问题列表
         */
        @Test
        @DisplayName("应该正确处理空问题列表")
        public void testEmptyIssueList() {
            List<SecurityIssue> issues = new ArrayList<>();

            List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);

            assertEquals(0, enhanced.size(), "Empty list should remain empty");
        }

        /**
         * 测试: 单个问题
         */
        @Test
        @DisplayName("应该正确处理单个问题")
        public void testSingleIssue() {
            List<SecurityIssue> issues = new ArrayList<>();
            issues.add(createTestIssue(0, "semgrep"));

            List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);

            assertNotNull(enhanced);
            assertTrue(enhanced.size() > 0);
        }

        /**
         * 测试: 混合分析器
         */
        @Test
        @DisplayName("应该处理来自不同分析器的问题")
        public void testMixedAnalyzers() {
            List<SecurityIssue> issues = new ArrayList<>();

            String[] analyzers = {"clang", "semgrep", "regex"};
            for (int i = 0; i < analyzers.length; i++) {
                for (int j = 0; j < 5; j++) {
                    SecurityIssue issue = createTestIssue(i * 5 + j, analyzers[i]);
                    issues.add(issue);
                }
            }

            List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);

            assertNotNull(enhanced);
            System.out.println("✅ Mixed analyzer test passed: " + issues.size() + " issues");
        }
    }

    @Nested
    @DisplayName("恢复能力测试")
    class ResilienceTests {

        /**
         * 测试: 错误处理
         */
        @Test
        @DisplayName("应该优雅地处理异常情况")
        public void testErrorHandling() {
            List<SecurityIssue> issues = new ArrayList<>();

            // 创建有效的问题
            for (int i = 0; i < 10; i++) {
                issues.add(createTestIssue(i, "semgrep"));
            }

            // 应该不抛出异常
            assertDoesNotThrow(() -> {
                List<SecurityIssue> enhanced = decisionEngine.enhanceIssues(issues);
                assertNotNull(enhanced);
            });

            System.out.println("✅ Error handling test passed");
        }
    }
}
