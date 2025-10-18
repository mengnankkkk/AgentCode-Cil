package com.harmony.agent.tools;

import com.harmony.agent.tools.result.AnalysisResult;
import com.harmony.agent.tools.result.CompileResult;
import com.harmony.agent.tools.result.TestResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具执行器 - 调用真实开发工具获取反馈
 * 扩展现有系统命令框架以集成Maven、JUnit、SpotBugs
 */
public class ToolExecutor {

    private final File projectPath;
    private static final int DEFAULT_TIMEOUT_MINUTES = 10;

    // Maven编译错误模式: [ERROR] /path/to/File.java:[line,column] message
    private static final Pattern MAVEN_ERROR_PATTERN =
        Pattern.compile("\\[ERROR\\]\\s+(.+?):\\[(\\d+),(\\d+)\\]\\s+(.+)");

    // Maven测试结果模式: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
    private static final Pattern TEST_SUMMARY_PATTERN =
        Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");

    // SpotBugs bug模式 (简化版,实际需要解析XML输出)
    private static final Pattern SPOTBUGS_PATTERN =
        Pattern.compile("\\[(.+?)\\]\\s+(.+?):(\\d+)\\s+-\\s+(.+)");

    public ToolExecutor(File projectPath) {
        this.projectPath = projectPath;
    }

    /**
     * Maven编译集成
     * @param cleanFirst 是否先执行clean
     * @return 编译结果
     */
    public CompileResult compileMaven(boolean cleanFirst) {
        long startTime = System.currentTimeMillis();
        String command = cleanFirst ? "mvn clean compile" : "mvn compile";

        try {
            ProcessResult result = executeCommand(command, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            // 解析编译错误
            List<CompileResult.CompileError> errors = parseCompileErrors(result.output);

            boolean success = result.exitCode == 0 && errors.isEmpty();

            return new CompileResult(success, result.output, errors, result.exitCode, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            List<CompileResult.CompileError> errors = new ArrayList<>();
            errors.add(new CompileResult.CompileError(
                "ToolExecutor", 0, 0,
                "Compilation failed: " + e.getMessage(),
                "ERROR"
            ));
            return new CompileResult(false, e.getMessage(), errors, -1, duration);
        }
    }

    /**
     * JUnit测试集成
     * @param testPattern 测试模式 (如 "*Test", "com.example.MyTest")
     * @return 测试结果
     */
    public TestResult runTests(String testPattern) {
        long startTime = System.currentTimeMillis();
        String command = testPattern != null && !testPattern.isEmpty()
            ? "mvn test -Dtest=" + testPattern
            : "mvn test";

        try {
            ProcessResult result = executeCommand(command, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            // 解析测试结果
            TestStats stats = parseTestResults(result.output);
            List<TestResult.TestFailure> failures = parseTestFailures(result.output);

            boolean success = result.exitCode == 0 && stats.failures == 0 && stats.errors == 0;

            return new TestResult(
                success, result.output,
                stats.testsRun, stats.testsRun - stats.failures - stats.errors - stats.skipped,
                stats.failures + stats.errors, stats.skipped,
                failures, result.exitCode, duration
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return new TestResult(
                false, e.getMessage(),
                0, 0, 0, 0,
                new ArrayList<>(), -1, duration
            );
        }
    }

    /**
     * SpotBugs静态分析集成
     * @param sourceFile 要分析的源文件 (可选,为null则分析整个项目)
     * @return 分析结果
     */
    public AnalysisResult analyzeWithSpotBugs(String sourceFile) {
        long startTime = System.currentTimeMillis();
        // 使用SpotBugs Maven插件
        String command = "mvn spotbugs:check";

        try {
            ProcessResult result = executeCommand(command, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            // 解析SpotBugs结果
            List<AnalysisResult.Bug> bugs = parseSpotBugsOutput(result.output);

            // SpotBugs通常在发现bug时返回非0退出码
            boolean success = bugs.isEmpty();

            return new AnalysisResult(success, result.output, bugs, result.exitCode, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return new AnalysisResult(false, e.getMessage(), new ArrayList<>(), -1, duration);
        }
    }

    /**
     * 执行命令并捕获输出
     */
    private ProcessResult executeCommand(String command, int timeoutMinutes) throws IOException, InterruptedException {
        String[] shellCommand = getShellCommand(command);

        ProcessBuilder pb = new ProcessBuilder(shellCommand);
        pb.directory(projectPath);
        pb.redirectErrorStream(true); // 合并stderr到stdout

        Process process = pb.start();

        // 读取输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // 等待完成(带超时)
        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        int exitCode = finished ? process.exitValue() : -1;

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Command timed out after " + timeoutMinutes + " minutes");
        }

        return new ProcessResult(exitCode, output.toString());
    }

    /**
     * 获取平台特定的shell命令
     */
    private String[] getShellCommand(String command) {
        if (isWindows()) {
            return new String[]{"cmd", "/c", command};
        } else {
            return new String[]{"sh", "-c", command};
        }
    }

    /**
     * 判断是否为Windows平台
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 解析Maven编译错误
     */
    private List<CompileResult.CompileError> parseCompileErrors(String output) {
        List<CompileResult.CompileError> errors = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            Matcher matcher = MAVEN_ERROR_PATTERN.matcher(line);
            if (matcher.find()) {
                String file = matcher.group(1);
                int lineNum = Integer.parseInt(matcher.group(2));
                int column = Integer.parseInt(matcher.group(3));
                String message = matcher.group(4);

                errors.add(new CompileResult.CompileError(
                    file, lineNum, column, message, "ERROR"
                ));
            }
        }

        return errors;
    }

    /**
     * 解析测试结果统计
     */
    private TestStats parseTestResults(String output) {
        String[] lines = output.split("\n");

        for (String line : lines) {
            Matcher matcher = TEST_SUMMARY_PATTERN.matcher(line);
            if (matcher.find()) {
                return new TestStats(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4))
                );
            }
        }

        return new TestStats(0, 0, 0, 0);
    }

    /**
     * 解析测试失败详情
     */
    private List<TestResult.TestFailure> parseTestFailures(String output) {
        List<TestResult.TestFailure> failures = new ArrayList<>();
        // 简化版:仅提取失败的测试方法名
        // 实际实现需要解析完整的堆栈跟踪

        String[] lines = output.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("FAILURE!") || line.contains("ERROR!")) {
                // 尝试提取测试类和方法
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1];
                    failures.add(new TestResult.TestFailure(
                        "TestClass", "testMethod",
                        line.trim(),
                        "Stack trace not implemented yet"
                    ));
                }
            }
        }

        return failures;
    }

    /**
     * 解析SpotBugs输出
     */
    private List<AnalysisResult.Bug> parseSpotBugsOutput(String output) {
        List<AnalysisResult.Bug> bugs = new ArrayList<>();
        // 简化版:实际应该解析SpotBugs的XML报告
        // 这里仅做文本解析示例

        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("[WARN]") || line.contains("[ERROR]")) {
                Matcher matcher = SPOTBUGS_PATTERN.matcher(line);
                if (matcher.find()) {
                    String priority = matcher.group(1);
                    String file = matcher.group(2);
                    int lineNum = Integer.parseInt(matcher.group(3));
                    String message = matcher.group(4);

                    bugs.add(new AnalysisResult.Bug(
                        file, lineNum, "BugType", "Category",
                        priority, message
                    ));
                }
            }
        }

        return bugs;
    }

    /**
     * 进程执行结果
     */
    private static class ProcessResult {
        final int exitCode;
        final String output;

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    /**
     * 测试统计
     */
    private static class TestStats {
        final int testsRun;
        final int failures;
        final int errors;
        final int skipped;

        TestStats(int testsRun, int failures, int errors, int skipped) {
            this.testsRun = testsRun;
            this.failures = failures;
            this.errors = errors;
            this.skipped = skipped;
        }
    }
}
