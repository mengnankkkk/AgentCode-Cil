package com.harmony.agent.test.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * E2E 测试基础框架
 *
 * 提供:
 * - 测试环境管理 (临时目录)
 * - 源码下载和初始化
 * - CLI 命令执行
 * - 结果验证工具
 *
 * 用法:
 * try (E2ETestFramework framework = new E2ETestFramework()) {
 *     framework.downloadSourceCode("bzip2");
 *     ProcessResult result = framework.executeAnalyze(...);
 *     framework.verify(result, ...);
 * }
 */
public class E2ETestFramework implements AutoCloseable {

    private static final String TEST_OUTPUT_DIR = "test-results/e2e";
    private static final long COMMAND_TIMEOUT_SECONDS = 600;  // 10 分钟

    private Path testDir;
    private Path sourceDir;
    private Path outputDir;
    private Map<String, String> testResults = new LinkedHashMap<>();

    public E2ETestFramework() throws IOException {
        setupDirectories();
    }

    /**
     * 设置测试目录结构
     */
    private void setupDirectories() throws IOException {
        testDir = Files.createTempDirectory("harmony-e2e-test-");
        sourceDir = testDir.resolve("sources");
        outputDir = testDir.resolve("output");

        Files.createDirectories(sourceDir);
        Files.createDirectories(outputDir);

        System.out.println("✅ Test environment created: " + testDir);
        System.out.println("   Sources: " + sourceDir);
        System.out.println("   Output: " + outputDir);
    }

    /**
     * 下载源码 (使用 git 或本地路径)
     */
    public void downloadSourceCode(String projectName) throws IOException, InterruptedException {
        Path projectDir = sourceDir.resolve(projectName);

        // 如果已存在，跳过下载
        if (Files.exists(projectDir)) {
            System.out.println("✅ Source code already exists: " + projectDir);
            return;
        }

        Files.createDirectories(projectDir);

        // 根据项目名选择下载方式
        if ("bzip2".equalsIgnoreCase(projectName)) {
            downloadBzip2(projectDir);
        } else if ("ylong_runtime".equalsIgnoreCase(projectName)) {
            downloadYlongRuntime(projectDir);
        } else {
            throw new IllegalArgumentException("Unknown project: " + projectName);
        }
    }

    /**
     * 下载 bzip2 源码
     */
    private void downloadBzip2(Path destDir) throws IOException, InterruptedException {
        System.out.println("📥 Downloading bzip2 source code...");

        ProcessBuilder pb = new ProcessBuilder("git", "clone",
            "https://github.com/carlosxl/bzip2.git",
            destDir.toString());

        Process process = pb.start();
        boolean completed = process.waitFor(5, TimeUnit.MINUTES);

        if (!completed || process.exitValue() != 0) {
            System.err.println("❌ Failed to download bzip2");
            // 使用本地测试样本
            createBzip2TestSample(destDir);
        } else {
            System.out.println("✅ bzip2 downloaded successfully");
        }
    }

    /**
     * 下载 ylong_runtime 源码
     */
    private void downloadYlongRuntime(Path destDir) throws IOException, InterruptedException {
        System.out.println("📥 Downloading ylong_runtime source code...");

        ProcessBuilder pb = new ProcessBuilder("git", "clone",
            "https://gitee.com/openharmony/ylong_runtime.git",
            destDir.toString());

        Process process = pb.start();
        boolean completed = process.waitFor(5, TimeUnit.MINUTES);

        if (!completed || process.exitValue() != 0) {
            System.err.println("❌ Failed to download ylong_runtime");
            createYlongRuntimeTestSample(destDir);
        } else {
            System.out.println("✅ ylong_runtime downloaded successfully");
        }
    }

    /**
     * 创建 bzip2 测试样本 (内存中的演示代码)
     */
    private void createBzip2TestSample(Path destDir) throws IOException {
        System.out.println("📝 Creating bzip2 test sample...");

        // 创建 bzlib.c 样本文件
        String bzlibCode = """
            #include <stdlib.h>
            #include <string.h>

            typedef struct {
                unsigned int bufsize;
                unsigned int nextIn;
                unsigned int* buf;
            } BZ_Stream;

            // Vulnerable: Buffer overflow without bounds checking
            int BZ_bzBuffToRelease(BZ_Stream* strm, char* buf, int len) {
                if (strm == NULL) return -1;

                // VULNERABILITY: No null check on buf
                strcpy(strm->buf, buf);  // Buffer overflow!

                return len;
            }

            // Vulnerable: Integer overflow
            unsigned int BZ_bzCompress(BZ_Stream* strm, int action) {
                unsigned int size = strm->bufsize;
                unsigned int new_size = size * 2;  // Integer overflow!

                if (new_size < size) {
                    return -1;
                }

                strm->buf = (unsigned int*)realloc(strm->buf, new_size);
                return 0;
            }

            // Vulnerable: Use-after-free
            void BZ_bzDecompress(BZ_Stream* strm) {
                free(strm->buf);
                // Use-after-free!
                memset(strm->buf, 0, strm->bufsize);
            }
            """;

        Files.write(destDir.resolve("bzlib.c"), bzlibCode.getBytes());
        System.out.println("✅ bzip2 test sample created");
    }

    /**
     * 创建 ylong_runtime 测试样本
     */
    private void createYlongRuntimeTestSample(Path destDir) throws IOException {
        System.out.println("📝 Creating ylong_runtime test sample...");

        String rustCode = """
            // Example of unsafe Rust code in ylong_runtime

            pub unsafe fn process_data(ptr: *mut u8, len: usize) {
                // SAFETY: No checks!
                let data = std::slice::from_raw_parts_mut(ptr, len);

                // Potential issue: No bounds checking
                data[1000] = 42;  // May panic!
            }

            pub struct Container {
                data: Vec<u8>,
            }

            impl Container {
                pub unsafe fn dangerous_access(&self) {
                    // SAFETY: Missing documentation
                    let ptr = self.data.as_ptr() as *mut u8;
                    *ptr = 255;
                }
            }
            """;

        Files.write(destDir.resolve("lib.rs"), rustCode.getBytes());
        System.out.println("✅ ylong_runtime test sample created");
    }

    /**
     * 执行 analyze 命令
     */
    public ProcessResult executeAnalyze(String projectName, String level)
            throws IOException, InterruptedException {
        Path projectPath = sourceDir.resolve(projectName);
        Path reportPath = outputDir.resolve(projectName + "-report.html");

        List<String> command = Arrays.asList(
            "java", "-jar", "target/harmony-safe-agent.jar",
            "analyze", projectPath.toString(),
            "--level", level,
            "-o", reportPath.toString()
        );

        return executeCommand(command, "analyze-" + projectName);
    }

    /**
     * 执行 refactor 命令 (Rust 迁移)
     */
    public ProcessResult executeRefactor(String projectName, String file, int line)
            throws IOException, InterruptedException {
        Path projectPath = sourceDir.resolve(projectName);

        List<String> command = Arrays.asList(
            "java", "-jar", "target/harmony-safe-agent.jar",
            "refactor", projectPath.toString(),
            "--type", "rust-migration",
            "-f", file,
            "-l", String.valueOf(line)
        );

        return executeCommand(command, "refactor-" + projectName);
    }

    /**
     * 执行 cache-stats 命令
     */
    public ProcessResult executeCacheStats()
            throws IOException, InterruptedException {
        List<String> command = Arrays.asList(
            "java", "-jar", "target/harmony-safe-agent.jar",
            "cache-stats", "--verbose"
        );

        return executeCommand(command, "cache-stats");
    }

    /**
     * 执行任意 CLI 命令
     */
    private ProcessResult executeCommand(List<String> command, String testName)
            throws IOException, InterruptedException {
        System.out.println("\n🔧 Executing: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File("."));
        pb.redirectErrorStream(true);

        long startTime = System.currentTimeMillis();
        Process process = pb.start();

        // 读取输出
        String output = readProcessOutput(process);

        // 等待完成
        boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        int exitCode = process.exitValue();
        ProcessResult result = new ProcessResult(
            testName, exitCode, output, duration, completed
        );

        // 记录结果
        testResults.put(testName, result.toString());

        return result;
    }

    /**
     * 读取进程输出
     */
    private String readProcessOutput(Process process) throws IOException {
        return new String(process.getInputStream().readAllBytes()) +
               "\n" + new String(process.getErrorStream().readAllBytes());
    }

    /**
     * 验证 analyze 结果
     */
    public void verifyAnalyzeResult(ProcessResult result, String expectedReportType) {
        System.out.println("\n✓ Verifying analyze result...");

        assert result.exitCode == 0 : "❌ analyze failed with exit code " + result.exitCode;
        assert result.output.contains("issues") : "❌ No issues in output";
        assert result.output.contains(expectedReportType) ||
               Files.exists(outputDir.resolve("bzip2-report.html")) :
            "❌ Report file not generated";

        System.out.println("✅ analyze verification passed");
        System.out.println("   Duration: " + result.duration + "ms");
        System.out.println("   Issues found: " + extractIssueCount(result.output));
    }

    /**
     * 验证 refactor (Rust 迁移) 结果
     */
    public void verifyRefactorResult(ProcessResult result) {
        System.out.println("\n✓ Verifying refactor result...");

        assert result.exitCode == 0 : "❌ refactor failed with exit code " + result.exitCode;
        assert !result.output.contains("占位符") : "❌ Still showing placeholder";
        assert result.output.contains("Rust") ||
               result.output.contains("FFI") ||
               result.output.contains("extern") :
            "❌ No Rust content in output";

        // 验证包含代码示例
        assert result.output.contains("```rust") ||
               result.output.contains("extern \"C\"") :
            "❌ No code examples in output";

        System.out.println("✅ refactor verification passed");
        System.out.println("   Rust migration advice generated");
        System.out.println("   Includes code examples: ✓");
    }

    /**
     * 验证缓存统计
     */
    public void verifyCacheStats(ProcessResult result) {
        System.out.println("\n✓ Verifying cache stats...");

        assert result.exitCode == 0 : "❌ cache-stats failed";
        assert result.output.contains("Hits") ||
               result.output.contains("Cache") :
            "❌ No cache statistics in output";

        System.out.println("✅ cache-stats verification passed");
        System.out.println("   Statistics displayed: ✓");
    }

    /**
     * 提取问题数量
     */
    private int extractIssueCount(String output) {
        // 简单的正则提取（实际可能需要更复杂的逻辑）
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("Found") && line.contains("issues")) {
                try {
                    return Integer.parseInt(line.replaceAll("\\D+", ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    /**
     * 生成测试报告
     */
    public void generateTestReport() throws IOException {
        Path reportPath = Paths.get(TEST_OUTPUT_DIR, "test-report.txt");
        Files.createDirectories(reportPath.getParent());

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("  E2E Test Report\n");
        sb.append("═══════════════════════════════════════\n\n");

        for (Map.Entry<String, String> entry : testResults.entrySet()) {
            sb.append("Test: ").append(entry.getKey()).append("\n");
            sb.append(entry.getValue()).append("\n\n");
        }

        Files.write(reportPath, sb.toString().getBytes());
        System.out.println("\n📊 Test report saved: " + reportPath);
    }

    /**
     * 清理测试环境
     */
    @Override
    public void close() throws Exception {
        System.out.println("\n🧹 Cleaning up test environment...");
        // 可选：删除临时目录
        // FileUtils.deleteDirectory(testDir.toFile());
        System.out.println("✅ Cleanup complete");
    }

    /**
     * 进程结果数据类
     */
    public static class ProcessResult {
        public final String testName;
        public final int exitCode;
        public final String output;
        public final long duration;
        public final boolean completed;

        public ProcessResult(String testName, int exitCode, String output,
                           long duration, boolean completed) {
            this.testName = testName;
            this.exitCode = exitCode;
            this.output = output;
            this.duration = duration;
            this.completed = completed;
        }

        public boolean isSuccess() {
            return exitCode == 0 && completed;
        }

        @Override
        public String toString() {
            return String.format(
                "  Exit Code: %d\n" +
                "  Duration: %dms\n" +
                "  Completed: %s\n" +
                "  Output length: %d bytes",
                exitCode, duration, completed, output.length()
            );
        }
    }
}
