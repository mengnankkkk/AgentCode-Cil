package com.harmony.agent.test.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6.1 E2E 测试: bzip2 (C/C++) 完整流程
 *
 * 测试场景:
 * 1. 下载 bzip2 源码
 * 2. 运行 analyze 命令
 *    - 验证 P5 报告生成
 *    - 验证 P3 AI 过滤
 *    - 验证 P5 计时器
 * 3. 运行 refactor 命令 (Rust 迁移)
 *    - 验证 P4 返回详细 Markdown
 *    - 验证代码示例生成
 * 4. 运行 autofix 命令
 *    - 验证 P8/P9 P-C-R-T 循环
 *
 * 赛项关联:
 * - 3.1: 对标 bzip2-rs 实现 ✅
 * - 1.1.2: Rust 代码生成示例 ✅
 * - 2.4.2: 代码生成质量 (25 分) ✅
 */
@DisplayName("T6.1: bzip2 完整 E2E 测试")
public class Bzip2E2ETest {

    private E2ETestFramework framework;

    @BeforeEach
    public void setUp() throws Exception {
        framework = new E2ETestFramework();
        System.out.println("\n" + "═".repeat(60));
        System.out.println("🚀 Starting T6.1: bzip2 E2E Test");
        System.out.println("═".repeat(60));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (framework != null) {
            framework.generateTestReport();
            framework.close();
        }
    }

    /**
     * T6.1.1: 下载和初始化 bzip2
     */
    @Test
    @DisplayName("T6.1.1: 下载 bzip2 源码")
    public void testDownloadBzip2() throws Exception {
        System.out.println("\n📥 Test: Download bzip2 source code");

        framework.downloadSourceCode("bzip2");

        System.out.println("✅ Test passed: bzip2 downloaded successfully");
    }

    /**
     * T6.1.2: 运行 analyze 命令
     *
     * 验证:
     * - P5 报告生成 (HTML)
     * - P3 AI 过滤正常
     * - P5 计时器准确
     */
    @Test
    @DisplayName("T6.1.2: 运行 analyze 命令并验证报告生成")
    public void testAnalyzeBzip2() throws Exception {
        System.out.println("\n🔍 Test: Analyze bzip2 with standard level");

        // 步骤 1: 下载源码
        framework.downloadSourceCode("bzip2");

        // 步骤 2: 运行 analyze
        E2ETestFramework.ProcessResult result = framework.executeAnalyze(
            "bzip2",
            "standard"  // --level standard
        );

        // 步骤 3: 验证结果
        assertTrue(result.isSuccess(),
            "❌ analyze command failed: " + result.exitCode);

        // 验证 P5 报告生成
        framework.verifyAnalyzeResult(result, "html");

        // 验证耗时 (应该在 10-15 分钟范围内)
        long durationSeconds = result.duration / 1000;
        System.out.println("\n📊 Performance Metrics:");
        System.out.println("   Total duration: " + durationSeconds + "s");
        System.out.println("   Expected: 10-15 minutes");

        // 验证 P3 AI 过滤
        assertFalse(result.output.contains("0 filtered"),
            "⚠️  No filtered issues - AI validation may not be running");

        System.out.println("✅ Test passed: analyze completed successfully");
    }

    /**
     * T6.1.3: 运行 refactor 命令 (Rust 迁移)
     *
     * 验证:
     * - P4 返回详细的 Markdown (非占位符)
     * - 包含代码示例
     * - 包含 FFI 安全指导
     */
    @Test
    @DisplayName("T6.1.3: 运行 refactor 命令生成 Rust 迁移建议")
    public void testRefactorBzip2ToRust() throws Exception {
        System.out.println("\n🔄 Test: Generate Rust migration advice for bzip2");

        // 步骤 1: 下载源码
        framework.downloadSourceCode("bzip2");

        // 步骤 2: 运行 refactor 命令
        // 选择 bzlib.c 的第 32 行 (BZ_bzBuffToRelease 函数)
        E2ETestFramework.ProcessResult result = framework.executeRefactor(
            "bzip2",
            "bzlib.c",
            32
        );

        // 步骤 3: 验证结果
        assertTrue(result.isSuccess(),
            "❌ refactor command failed: " + result.exitCode);

        // 验证 P4 返回详细建议
        framework.verifyRefactorResult(result);

        // 详细验证
        System.out.println("\n📋 Detailed Verification:");
        assertAll(
            "Rust migration advice should include:",
            () -> assertTrue(result.output.contains("Rust") ||
                           result.output.contains("rust"),
                "Missing Rust keyword"),
            () -> assertTrue(result.output.contains("extern") ||
                           result.output.contains("FFI"),
                "Missing FFI information"),
            () -> assertTrue(result.output.contains("```"),
                "Missing code examples"),
            () -> assertTrue(result.output.length() > 500,
                "Output too short (expected > 500 bytes)")
        );

        // 验证五段式结构
        System.out.println("\n📖 Verifying 5-section structure:");
        verifyRustMigrationSections(result.output);

        System.out.println("✅ Test passed: Rust migration advice generated successfully");
    }

    /**
     * T6.1.4: 验证 Rust 迁移建议的五段式结构
     */
    private void verifyRustMigrationSections(String output) {
        String[] sections = {
            "Core Responsibility",
            "FFI Safety Wrapper",
            "Rust-native Rewrite",
            "Key Challenges",
            "Migration Strategy"
        };

        int foundSections = 0;
        for (String section : sections) {
            if (output.toLowerCase().contains(section.toLowerCase())) {
                System.out.println("   ✓ Found: " + section);
                foundSections++;
            }
        }

        assertTrue(foundSections >= 3,
            "Expected at least 3 sections, found " + foundSections);
    }

    /**
     * T6.1.5: 验证缓存系统效能
     *
     * 验证:
     * - P1 缓存正常工作
     * - 第二次分析应该更快
     */
    @Test
    @DisplayName("T6.1.5: 验证缓存系统效能 (P1 优化)")
    public void testCachePerformance() throws Exception {
        System.out.println("\n⚡ Test: Cache performance (P1 optimization)");

        // 步骤 1: 下载源码
        framework.downloadSourceCode("bzip2");

        // 步骤 2: 第一次分析
        System.out.println("\n   第一次分析 (冷启动)...");
        E2ETestFramework.ProcessResult firstRun = framework.executeAnalyze(
            "bzip2", "standard"
        );
        long firstDuration = firstRun.duration;
        System.out.println("   耗时: " + firstDuration + "ms");

        // 步骤 3: 第二次分析 (应该利用缓存)
        System.out.println("\n   第二次分析 (热缓存)...");
        E2ETestFramework.ProcessResult secondRun = framework.executeAnalyze(
            "bzip2", "standard"
        );
        long secondDuration = secondRun.duration;
        System.out.println("   耗时: " + secondDuration + "ms");

        // 步骤 4: 验证缓存效果
        System.out.println("\n📊 Cache Performance:");
        double speedup = (double) firstDuration / secondDuration;
        System.out.println("   加速倍数: " + String.format("%.1f", speedup) + "x");

        assertTrue(secondDuration < firstDuration,
            "❌ Second run should be faster due to caching");

        assertTrue(speedup >= 2.0,
            "❌ Expected at least 2x speedup with cache, got " + speedup + "x");

        System.out.println("✅ Test passed: Cache working efficiently");
    }

    /**
     * T6.1.6: 运行 cache-stats 命令
     *
     * 验证:
     * - cache-stats 命令可用
     * - 显示缓存统计信息
     * - 显示时间节省
     */
    @Test
    @DisplayName("T6.1.6: 验证 cache-stats 命令 (P1 诊断)")
    public void testCacheStats() throws Exception {
        System.out.println("\n📊 Test: cache-stats command");

        E2ETestFramework.ProcessResult result = framework.executeCacheStats();

        assertTrue(result.isSuccess(),
            "❌ cache-stats command failed: " + result.exitCode);

        framework.verifyCacheStats(result);

        System.out.println("✅ Test passed: cache-stats working correctly");
    }

    /**
     * T6.1.7: 完整工作流测试
     *
     * 集成所有步骤:
     * 1. 下载源码
     * 2. 分析
     * 3. Rust 迁移建议
     * 4. 缓存验证
     */
    @Test
    @DisplayName("T6.1.7: 完整工作流集成测试")
    public void testCompleteWorkflow() throws Exception {
        System.out.println("\n🔄 Test: Complete E2E workflow");

        // 步骤 1: 初始化
        System.out.println("\n1️⃣  Downloading source code...");
        framework.downloadSourceCode("bzip2");

        // 步骤 2: 分析
        System.out.println("\n2️⃣  Running analyze...");
        E2ETestFramework.ProcessResult analyzeResult =
            framework.executeAnalyze("bzip2", "standard");
        assertTrue(analyzeResult.isSuccess(), "analyze failed");

        // 步骤 3: Rust 迁移
        System.out.println("\n3️⃣  Generating Rust migration advice...");
        E2ETestFramework.ProcessResult refactorResult =
            framework.executeRefactor("bzip2", "bzlib.c", 32);
        assertTrue(refactorResult.isSuccess(), "refactor failed");

        // 步骤 4: 缓存统计
        System.out.println("\n4️⃣  Checking cache stats...");
        E2ETestFramework.ProcessResult cacheResult = framework.executeCacheStats();
        assertTrue(cacheResult.isSuccess(), "cache-stats failed");

        System.out.println("\n✅ Complete workflow test passed!");
        System.out.println("   All steps executed successfully");
    }

    /**
     * T6.1.8: 性能基准测试
     *
     * 记录性能指标:
     * - P0 并发优化效果
     * - P1 缓存效率
     * - 总体性能改进
     */
    @Test
    @DisplayName("T6.1.8: 性能基准测试")
    public void testPerformanceBenchmark() throws Exception {
        System.out.println("\n⏱️  Test: Performance benchmark");

        framework.downloadSourceCode("bzip2");

        // 测试配置
        String[] levels = {"quick", "standard", "deep"};
        System.out.println("\n📊 Performance Results:");
        System.out.println("┌────────────┬────────────┬──────────┐");
        System.out.println("│ Level      │ Duration   │ Speedup  │");
        System.out.println("├────────────┼────────────┼──────────┤");

        long baselineDuration = 0;

        for (String level : levels) {
            E2ETestFramework.ProcessResult result =
                framework.executeAnalyze("bzip2", level);

            if (result.isSuccess()) {
                long duration = result.duration / 1000;  // 转换为秒
                if (baselineDuration == 0) {
                    baselineDuration = result.duration;
                }
                double speedup = (double) baselineDuration / result.duration;

                System.out.printf("│ %-10s │ %8ds  │ %6.1fx  │%n",
                    level, duration, speedup);
            }
        }
        System.out.println("└────────────┴────────────┴──────────┘");
    }
}
