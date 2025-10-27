package com.harmony.agent.tools;

import com.harmony.agent.tools.result.AnalysisResult;
import com.harmony.agent.tools.result.CompileResult;
import com.harmony.agent.tools.result.TestResult;
import com.harmony.agent.tools.result.ScriptResult;

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
 * 扩展现有系统命令框架以集成Maven、JUnit、SpotBugs、Bash、C++、Rust
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

    // C++ 编译错误模式: /path/to/file.cpp:10:5: error: message
    private static final Pattern CPP_ERROR_PATTERN =
        Pattern.compile("(.+?):(\\d+):(\\d+):\\s+(error|warning):\\s+(.+)");

    // Rust 编译错误模式: error[E0xxx]: message
    private static final Pattern RUST_ERROR_PATTERN =
        Pattern.compile("^error\\[(.+?)\\]:\\s+(.+)$", Pattern.MULTILINE);

    // Rust 测试结果模式: test result: ok. 5 passed; 0 failed
    private static final Pattern RUST_TEST_PATTERN =
        Pattern.compile("test result: (ok|FAILED)\\..*(\\d+) passed.*?(\\d+) failed");

    public ToolExecutor(File projectPath) {
        this.projectPath = projectPath;
    }

    // ==================== Bash/Shell 支持 ====================

    /**
     * 执行 Bash/Shell 脚本
     * @param scriptPath 脚本路径（相对于项目路径或绝对路径）
     * @return 脚本执行结果
     */
    public ScriptResult runBashScript(String scriptPath) {
        long startTime = System.currentTimeMillis();

        try {
            File script = new File(scriptPath);
            if (!script.isAbsolute()) {
                script = new File(projectPath, scriptPath);
            }

            if (!script.exists()) {
                return new ScriptResult(false, "", "脚本不存在: " + script.getAbsolutePath(), -1,
                    System.currentTimeMillis() - startTime);
            }

            // 确保脚本可执行（Unix/Linux）
            if (!isWindows()) {
                executeCommand("chmod +x " + script.getAbsolutePath(), 5);
            }

            String command = isWindows()
                ? "powershell -ExecutionPolicy Bypass -File \"" + script.getAbsolutePath() + "\""
                : script.getAbsolutePath();

            ProcessResult result = executeCommand(command, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            boolean success = result.exitCode == 0;
            return new ScriptResult(success, result.output, "", result.exitCode, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return new ScriptResult(false, "", e.getMessage(), -1, duration);
        }
    }

    /**
     * 执行任意 Bash/Shell 命令
     * @param bashCommand Bash 命令字符串
     * @return 脚本执行结果
     */
    public ScriptResult runBashCommand(String bashCommand) {
        long startTime = System.currentTimeMillis();

        try {
            ProcessResult result = executeCommand(bashCommand, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            boolean success = result.exitCode == 0;
            return new ScriptResult(success, result.output, "", result.exitCode, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return new ScriptResult(false, "", e.getMessage(), -1, duration);
        }
    }

    // ==================== C++ 支持 ====================

    /**
     * 编译 C++ 项目（支持 CMake、Make、direct compilation）
     * @param cleanFirst 是否先清理
     * @return 编译结果
     */
    public CompileResult compileCpp(boolean cleanFirst) {
        long startTime = System.currentTimeMillis();

        try {
            // 检测构建系统
            String buildCommand = detectCppBuildSystem(cleanFirst);

            if (buildCommand == null) {
                throw new IOException("未能检测到 C++ 构建系统（CMakeLists.txt、Makefile 或编译脚本）");
            }

            ProcessResult result = executeCommand(buildCommand, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            List<CompileResult.CompileError> errors = parseCppCompileErrors(result.output);
            boolean success = result.exitCode == 0 && errors.isEmpty();

            return new CompileResult(success, result.output, errors, result.exitCode, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            List<CompileResult.CompileError> errors = new ArrayList<>();
            errors.add(new CompileResult.CompileError(
                "ToolExecutor", 0, 0,
                "C++ compilation failed: " + e.getMessage(),
                "ERROR"
            ));
            return new CompileResult(false, e.getMessage(), errors, -1, duration);
        }
    }

    /**
     * 编译单个 C++ 源文件
     * @param sourceFile 源文件路径
     * @return 编译结果
     */
    public CompileResult compileCppFile(String sourceFile) {
        long startTime = System.currentTimeMillis();

        try {
            File source = new File(sourceFile);
            if (!source.isAbsolute()) {
                source = new File(projectPath, sourceFile);
            }

            if (!source.exists()) {
                throw new IOException("源文件不存在: " + source.getAbsolutePath());
            }

            // 生成输出文件名
            String outputFile = source.getAbsolutePath()
                .replaceAll("\\.cpp$|\\.cc$|\\.cxx$", ".o");

            String compiler = "g++"; // 默认使用 g++，也可以检测 clang++
            String command = compiler + " -c \"" + source.getAbsolutePath() + "\" -o \"" + outputFile + "\"";

            ProcessResult result = executeCommand(command, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            List<CompileResult.CompileError> errors = parseCppCompileErrors(result.output);
            boolean success = result.exitCode == 0 && errors.isEmpty();

            return new CompileResult(success, result.output, errors, result.exitCode, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            List<CompileResult.CompileError> errors = new ArrayList<>();
            errors.add(new CompileResult.CompileError(
                "ToolExecutor", 0, 0,
                "C++ file compilation failed: " + e.getMessage(),
                "ERROR"
            ));
            return new CompileResult(false, e.getMessage(), errors, -1, duration);
        }
    }

    /**
     * 运行 C++ 测试（如 Google Test、Catch2 等）
     * @param testPattern 测试可执行文件名或模式
     * @return 测试结果
     */
    public TestResult testCpp(String testPattern) {
        long startTime = System.currentTimeMillis();

        try {
            // 构造测试命令
            String testCommand;
            if (testPattern == null || testPattern.isEmpty()) {
                testCommand = isWindows() ? "test.exe" : "./test";
            } else {
                testCommand = isWindows()
                    ? (projectPath.getAbsolutePath() + "\\" + testPattern)
                    : (projectPath.getAbsolutePath() + "/" + testPattern);
            }

            ProcessResult result = executeCommand(testCommand, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            // 简化版：解析输出中的测试统计
            TestStats stats = parseCppTestOutput(result.output);
            List<TestResult.TestFailure> failures = parseCppTestFailures(result.output);

            boolean success = result.exitCode == 0 && stats.failures == 0;

            return new TestResult(
                success, result.output,
                stats.testsRun, stats.testsRun - stats.failures,
                stats.failures, stats.skipped,
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

    // ==================== Rust 支持 ====================

    /**
     * 编译 Rust 项目（使用 Cargo）
     * @return 编译结果
     */
    public CompileResult compileRust() {
        long startTime = System.currentTimeMillis();

        try {
            // 检查是否存在 Cargo.toml
            File cargoFile = new File(projectPath, "Cargo.toml");
            if (!cargoFile.exists()) {
                throw new IOException("Cargo.toml 不存在，请确认这是一个 Rust 项目");
            }

            String command = "cargo build";
            ProcessResult result = executeCommand(command, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            List<CompileResult.CompileError> errors = parseRustCompileErrors(result.output);
            boolean success = result.exitCode == 0 && errors.isEmpty();

            return new CompileResult(success, result.output, errors, result.exitCode, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            List<CompileResult.CompileError> errors = new ArrayList<>();
            errors.add(new CompileResult.CompileError(
                "ToolExecutor", 0, 0,
                "Rust compilation failed: " + e.getMessage(),
                "ERROR"
            ));
            return new CompileResult(false, e.getMessage(), errors, -1, duration);
        }
    }

    /**
     * 编译 Rust Release 版本
     * @return 编译结果
     */
    public CompileResult compileRustRelease() {
        long startTime = System.currentTimeMillis();

        try {
            File cargoFile = new File(projectPath, "Cargo.toml");
            if (!cargoFile.exists()) {
                throw new IOException("Cargo.toml 不存在");
            }

            String command = "cargo build --release";
            ProcessResult result = executeCommand(command, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            List<CompileResult.CompileError> errors = parseRustCompileErrors(result.output);
            boolean success = result.exitCode == 0 && errors.isEmpty();

            return new CompileResult(success, result.output, errors, result.exitCode, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            List<CompileResult.CompileError> errors = new ArrayList<>();
            errors.add(new CompileResult.CompileError(
                "ToolExecutor", 0, 0,
                "Rust release compilation failed: " + e.getMessage(),
                "ERROR"
            ));
            return new CompileResult(false, e.getMessage(), errors, -1, duration);
        }
    }

    /**
     * 运行 Rust 测试（使用 Cargo test）
     * @param testPattern 测试名称模式（可选）
     * @return 测试结果
     */
    public TestResult testRust(String testPattern) {
        long startTime = System.currentTimeMillis();

        try {
            File cargoFile = new File(projectPath, "Cargo.toml");
            if (!cargoFile.exists()) {
                throw new IOException("Cargo.toml 不存在");
            }

            String command = testPattern != null && !testPattern.isEmpty()
                ? "cargo test " + testPattern
                : "cargo test";

            ProcessResult result = executeCommand(command, DEFAULT_TIMEOUT_MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            // 解析 Rust 测试结果
            TestStats stats = parseRustTestOutput(result.output);
            List<TestResult.TestFailure> failures = parseRustTestFailures(result.output);

            boolean success = result.exitCode == 0 && stats.failures == 0;

            return new TestResult(
                success, result.output,
                stats.testsRun, stats.testsRun - stats.failures,
                stats.failures, stats.skipped,
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

    // ==================== Maven/Java 支持（原有功能）====================

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

    // ==================== 辅助方法 ====================

    /**
     * 检测 C++ 构建系统
     */
    private String detectCppBuildSystem(boolean cleanFirst) {
        File cmakeFile = new File(projectPath, "CMakeLists.txt");
        File makeFile = new File(projectPath, "Makefile");
        File makefileWin = new File(projectPath, "Makefile.win");

        if (cmakeFile.exists()) {
            // CMake 构建
            if (cleanFirst) {
                try {
                    executeCommand("cmake --build . --clean-first", 5);
                } catch (Exception e) {
                    // 忽略清理错误
                }
            }
            return "cmake --build .";
        } else if (makeFile.exists() || makefileWin.exists()) {
            // Make 构建
            String cleanCmd = cleanFirst ? (isWindows() ? "nmake clean" : "make clean && ") : "";
            return cleanCmd + (isWindows() ? "nmake" : "make");
        }
        // 尝试直接用 g++ 编译所有 .cpp 文件
        return null;
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

    // ==================== 解析方法 ====================

    /**
     * 解析 C++ 编译错误
     */
    private List<CompileResult.CompileError> parseCppCompileErrors(String output) {
        List<CompileResult.CompileError> errors = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            Matcher matcher = CPP_ERROR_PATTERN.matcher(line);
            if (matcher.find()) {
                String file = matcher.group(1);
                int lineNum = Integer.parseInt(matcher.group(2));
                int column = Integer.parseInt(matcher.group(3));
                String type = matcher.group(4); // error 或 warning
                String message = matcher.group(5);

                errors.add(new CompileResult.CompileError(
                    file, lineNum, column, message, type.toUpperCase()
                ));
            }
        }

        return errors;
    }

    /**
     * 解析 C++ 测试输出
     */
    private TestStats parseCppTestOutput(String output) {
        // Google Test 格式: [==========] X tests from Y test cases ran.
        // Catch2 格式: test cases: X | X failed

        int testsRun = 0;
        int failures = 0;

        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("test cases") && line.contains("passed")) {
                // Catch2 格式
                if (line.contains("failed")) {
                    String[] parts = line.split("\\s+");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("test") && i + 1 < parts.length) {
                            try {
                                testsRun = Integer.parseInt(parts[i + 1].replaceAll("[^0-9]", ""));
                            } catch (Exception e) {
                                // 忽略解析错误
                            }
                        }
                    }
                }
            }
        }

        return new TestStats(testsRun, failures, 0, 0);
    }

    /**
     * 解析 C++ 测试失败
     */
    private List<TestResult.TestFailure> parseCppTestFailures(String output) {
        List<TestResult.TestFailure> failures = new ArrayList<>();
        String[] lines = output.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("FAILED") || line.contains("Error")) {
                failures.add(new TestResult.TestFailure(
                    "TestClass", "testMethod",
                    line.trim(),
                    ""
                ));
            }
        }

        return failures;
    }

    /**
     * 解析 Rust 编译错误
     */
    private List<CompileResult.CompileError> parseRustCompileErrors(String output) {
        List<CompileResult.CompileError> errors = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            // Rust 错误格式: error[E0xxx]: message at src/main.rs:10:5
            if (line.contains("error[")) {
                Matcher matcher = RUST_ERROR_PATTERN.matcher(line);
                if (matcher.find()) {
                    String errorCode = matcher.group(1);
                    String message = matcher.group(2);

                    errors.add(new CompileResult.CompileError(
                        "src/main.rs", 0, 0, errorCode + ": " + message, "ERROR"
                    ));
                }
            } else if (line.matches(".*-->\\s+src/.*")) {
                // 提取文件位置
                try {
                    String[] parts = line.split(":");
                    if (parts.length >= 3) {
                        int lineNum = Integer.parseInt(parts[1].trim());
                        int column = Integer.parseInt(parts[2].trim());
                        if (!errors.isEmpty()) {
                            CompileResult.CompileError lastError = errors.get(errors.size() - 1);
                            errors.set(errors.size() - 1, new CompileResult.CompileError(
                                parts[0].replaceAll(".*-->\\s+", "").trim(),
                                lineNum, column,
                                lastError.getMessage(), lastError.getSeverity()
                            ));
                        }
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }

        return errors;
    }

    /**
     * 解析 Rust 测试输出
     */
    private TestStats parseRustTestOutput(String output) {
        String[] lines = output.split("\n");
        int testsRun = 0;
        int failures = 0;

        for (String line : lines) {
            // test result: ok. 5 passed; 0 failed
            // test result: FAILED. 3 passed; 2 failed
            if (line.contains("test result:")) {
                Matcher matcher = RUST_TEST_PATTERN.matcher(line);
                if (matcher.find()) {
                    String result = matcher.group(1);
                    int passed = Integer.parseInt(matcher.group(2));
                    int failed = Integer.parseInt(matcher.group(3));

                    testsRun = passed + failed;
                    failures = failed;
                }
            }
        }

        return new TestStats(testsRun, failures, 0, 0);
    }

    /**
     * 解析 Rust 测试失败
     */
    private List<TestResult.TestFailure> parseRustTestFailures(String output) {
        List<TestResult.TestFailure> failures = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.contains("test") && (line.contains("FAILED") || line.contains("... FAILED"))) {
                failures.add(new TestResult.TestFailure(
                    "TestModule", "test_function",
                    line.trim(),
                    ""
                ));
            }
        }

        return failures;
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

    // ==================== 内部类 ====================

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
