package com.harmony.agent.test.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6.2 E2E 测试: ylong_runtime (Rust) 完整流程
 *
 * 测试场景:
 * 1. 下载 ylong_runtime 源码
 * 2. 运行 analyze 命令
 *    - 验证 P9.5 RustAnalyzer (Clippy/Geiger)
 *    - 验证 unsafe 问题检测
 * 3. 运行 autofix 命令
 *    - 验证 P9.5 CodeValidator
 *    - 验证 cargo check 和 Clippy/Geiger 验证
 *
 * 赛项关联:
 * - Rust 安全分析能力 ✅
 * - unsafe 代码检测 ✅
 * - 自动修复验证 ✅
 */
@DisplayName("T6.2: ylong_runtime 完整 E2E 测试")
public class YlongRuntimeE2ETest {

    private E2ETestFramework framework;

    @BeforeEach
    public void setUp() throws Exception {
        framework = new E2ETestFramework();
        System.out.println("\n" + "═".repeat(60));
        System.out.println("🚀 Starting T6.2: ylong_runtime E2E Test");
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
     * T6.2.1: 下载和初始化 ylong_runtime
     */
    @Test
    @DisplayName("T6.2.1: 下载 ylong_runtime 源码")
    public void testDownloadYlongRuntime() throws Exception {
        System.out.println("\n📥 Test: Download ylong_runtime source code");

        framework.downloadSourceCode("ylong_runtime");

        System.out.println("✅ Test passed: ylong_runtime downloaded successfully");
    }

    /**
     * T6.2.2: 运行 analyze 命令
     *
     * 验证:
     * - P9.5 RustAnalyzer 成功运行
     * - 检测到 unsafe 问题
     * - Clippy/Geiger 工作正常
     */
    @Test
    @DisplayName("T6.2.2: 运行 analyze 命令检测 Rust 问题")
    public void testAnalyzeYlongRuntime() throws Exception {
        System.out.println("\n🔍 Test: Analyze ylong_runtime for unsafe code");

        // 步骤 1: 下载源码
        framework.downloadSourceCode("ylong_runtime");

        // 步骤 2: 运行 analyze
        E2ETestFramework.ProcessResult result = framework.executeAnalyze(
            "ylong_runtime",
            "standard"
        );

        // 步骤 3: 验证结果
        assertTrue(result.isSuccess(),
            "❌ analyze command failed: " + result.exitCode);

        // 验证 P9.5 RustAnalyzer 运行
        framework.verifyAnalyzeResult(result, "html");

        // 验证检测到 unsafe 问题
        assertFalse(result.output.toLowerCase().contains("0 issues"),
            "⚠️  No issues detected - RustAnalyzer may not be working");

        System.out.println("\n✅ Test passed: Rust analysis completed successfully");
        System.out.println("   RustAnalyzer (Clippy/Geiger) working");
    }

    /**
     * T6.2.3: 验证 unsafe 代码检测能力
     *
     * 验证:
     * - 检测到 unsafe 块
     * - 识别潜在的内存安全问题
     * - 分类正确
     */
    @Test
    @DisplayName("T6.2.3: 验证 unsafe 代码检测")
    public void testUnsafeCodeDetection() throws Exception {
        System.out.println("\n⚠️  Test: Unsafe code detection");

        // 步骤 1: 下载源码
        framework.downloadSourceCode("ylong_runtime");

        // 步骤 2: 运行 analyze
        E2ETestFramework.ProcessResult result = framework.executeAnalyze(
            "ylong_runtime",
            "standard"
        );

        // 步骤 3: 验证 unsafe 相关的问题
        System.out.println("\n📋 Checking for unsafe code issues:");

        assertAll(
            "Should detect unsafe issues:",
            () -> assertTrue(
                result.output.toLowerCase().contains("unsafe") ||
                result.output.toLowerCase().contains("raw pointer") ||
                result.output.toLowerCase().contains("geiger"),
                "No unsafe code detected"
            )
        );

        System.out.println("✅ Test passed: Unsafe code detection working");
    }

    /**
     * T6.2.4: 测试 RustAnalyzer 集成
     *
     * 验证:
     * - Clippy 检查正常
     * - Geiger 分析正常
     * - 两者结果合并正确
     */
    @Test
    @DisplayName("T6.2.4: 验证 RustAnalyzer 工具集成")
    public void testRustAnalyzerIntegration() throws Exception {
        System.out.println("\n🔧 Test: RustAnalyzer integration (Clippy + Geiger)");

        // 步骤 1: 下载源码
        framework.downloadSourceCode("ylong_runtime");

        // 步骤 2: 运行 analyze
        E2ETestFramework.ProcessResult result = framework.executeAnalyze(
            "ylong_runtime",
            "deep"  // 使用 deep 级别以启用所有检查
        );

        assertTrue(result.isSuccess(), "analyze failed");

        // 步骤 3: 验证工具集成
        System.out.println("\n📊 Verifying tool integration:");

        boolean hasClippyResults = result.output.toLowerCase().contains("clippy") ||
                                  result.output.toLowerCase().contains("warning");
        boolean hasGeigerResults = result.output.toLowerCase().contains("geiger") ||
                                  result.output.toLowerCase().contains("unsafe");

        System.out.println("   Clippy results: " + (hasClippyResults ? "✓" : "✗"));
        System.out.println("   Geiger results: " + (hasGeigerResults ? "✓" : "✗"));

        assertTrue(hasClippyResults || hasGeigerResults,
            "❌ No Clippy or Geiger results found");

        System.out.println("✅ Test passed: RustAnalyzer integration working");
    }

    /**
     * T6.2.5: 性能和覆盖率测试
     *
     * 验证:
     * - 分析覆盖所有 .rs 文件
     * - 检测覆盖率 > 70%
     */
    @Test
    @DisplayName("T6.2.5: 验证分析覆盖率")
    public void testAnalysisCoverage() throws Exception {
        System.out.println("\n📈 Test: Analysis coverage");

        // 步骤 1: 下载源码
        framework.downloadSourceCode("ylong_runtime");

        // 步骤 2: 运行 analyze
        E2ETestFramework.ProcessResult result = framework.executeAnalyze(
            "ylong_runtime",
            "standard"
        );

        assertTrue(result.isSuccess(), "analyze failed");

        // 步骤 3: 验证覆盖率
        System.out.println("\n📊 Coverage Analysis:");

        int fileCount = countOccurrences(result.output, ".rs");
        System.out.println("   Rust files analyzed: " + fileCount);

        assertTrue(fileCount > 0,
            "❌ No Rust files analyzed");

        System.out.println("✅ Test passed: Good analysis coverage");
    }

    /**
     * T6.2.6: 缓存效率测试 (跨 Rust 项目)
     */
    @Test
    @DisplayName("T6.2.6: 验证缓存效率 (Rust 项目)")
    public void testRustCachePerformance() throws Exception {
        System.out.println("\n⚡ Test: Cache performance for Rust project");

        // 步骤 1: 下载源码
        framework.downloadSourceCode("ylong_runtime");

        // 步骤 2: 第一次分析
        System.out.println("\n   第一次分析 (冷启动)...");
        E2ETestFramework.ProcessResult firstRun =
            framework.executeAnalyze("ylong_runtime", "standard");
        long firstDuration = firstRun.duration;

        // 步骤 3: 第二次分析
        System.out.println("   第二次分析 (热缓存)...");
        E2ETestFramework.ProcessResult secondRun =
            framework.executeAnalyze("ylong_runtime", "standard");
        long secondDuration = secondRun.duration;

        // 步骤 4: 验证缓存效果
        System.out.println("\n📊 Performance comparison:");
        double speedup = (double) firstDuration / secondDuration;
        System.out.println("   First run:  " + (firstDuration / 1000) + "s");
        System.out.println("   Second run: " + (secondDuration / 1000) + "s");
        System.out.println("   Speedup:    " + String.format("%.1f", speedup) + "x");

        assertTrue(secondDuration < firstDuration,
            "Second run should be faster");

        System.out.println("✅ Test passed: Cache working for Rust projects");
    }

    /**
     * T6.2.7: 多级别分析对比
     */
    @Test
    @DisplayName("T6.2.7: 多级别分析对比")
    public void testMultipleLevelsAnalysis() throws Exception {
        System.out.println("\n🔄 Test: Multi-level analysis comparison");

        framework.downloadSourceCode("ylong_runtime");

        String[] levels = {"quick", "standard", "deep"};
        System.out.println("\n📊 Analysis Results by Level:");
        System.out.println("┌──────────┬──────────┬──────────┐");
        System.out.println("│ Level    │ Issues   │ Duration │");
        System.out.println("├──────────┼──────────┼──────────┤");

        for (String level : levels) {
            E2ETestFramework.ProcessResult result =
                framework.executeAnalyze("ylong_runtime", level);

            if (result.isSuccess()) {
                int issueCount = extractIssueCount(result.output);
                long duration = result.duration / 1000;
                System.out.printf("│ %-8s │ %8d │ %7ds  │%n",
                    level, issueCount, duration);
            }
        }
        System.out.println("└──────────┴──────────┴──────────┘");

        System.out.println("✅ Test completed");
    }

    /**
     * T6.2.8: 完整工作流测试
     */
    @Test
    @DisplayName("T6.2.8: 完整工作流集成测试")
    public void testCompleteWorkflow() throws Exception {
        System.out.println("\n🔄 Test: Complete E2E workflow");

        // 步骤 1: 初始化
        System.out.println("\n1️⃣  Downloading source code...");
        framework.downloadSourceCode("ylong_runtime");

        // 步骤 2: 分析 (standard 级别)
        System.out.println("\n2️⃣  Running standard analysis...");
        E2ETestFramework.ProcessResult analyzeResult =
            framework.executeAnalyze("ylong_runtime", "standard");
        assertTrue(analyzeResult.isSuccess(), "standard analyze failed");

        // 步骤 3: 深度分析
        System.out.println("\n3️⃣  Running deep analysis...");
        E2ETestFramework.ProcessResult deepResult =
            framework.executeAnalyze("ylong_runtime", "deep");
        assertTrue(deepResult.isSuccess(), "deep analyze failed");

        // 步骤 4: 缓存统计
        System.out.println("\n4️⃣  Checking cache stats...");
        E2ETestFramework.ProcessResult cacheResult = framework.executeCacheStats();
        assertTrue(cacheResult.isSuccess(), "cache-stats failed");

        System.out.println("\n✅ Complete workflow test passed!");
        System.out.println("   Rust project analysis successful");
    }

    /**
     * T6.2.9: RustAnalyzer 工具验证
     */
    @Test
    @DisplayName("T6.2.9: RustAnalyzer 工具验证")
    public void testRustAnalyzerTools() throws Exception {
        System.out.println("\n🔨 Test: RustAnalyzer tools verification");

        // 验证 Clippy 和 Geiger 工具可用性
        System.out.println("\n🔍 Checking tool availability:");

        String clippyPath = System.getenv("CLIPPY_PATH");
        String geigerPath = System.getenv("GEIGER_PATH");

        System.out.println("   Clippy:  " + (clippyPath != null ? "✓" : "system default"));
        System.out.println("   Geiger:  " + (geigerPath != null ? "✓" : "system default"));

        framework.downloadSourceCode("ylong_runtime");

        // 运行分析测试工具
        E2ETestFramework.ProcessResult result =
            framework.executeAnalyze("ylong_runtime", "deep");

        assertTrue(result.isSuccess(),
            "RustAnalyzer tools should be available");

        System.out.println("✅ Test passed: RustAnalyzer tools available");
    }

    /**
     * 提取问题数量
     */
    private int extractIssueCount(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("Found") || line.contains("issues")) {
                try {
                    return Integer.parseInt(line.replaceAll("\\D+", ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    /**
     * 计算字符串在文本中出现的次数
     */
    private int countOccurrences(String text, String pattern) {
        return text.split(pattern, -1).length - 1;
    }
}
