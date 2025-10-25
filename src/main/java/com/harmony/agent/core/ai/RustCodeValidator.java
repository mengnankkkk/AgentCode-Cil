package com.harmony.agent.core.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Rust Code Validator - 验证和检查生成的 Rust 代码
 * 使用 rustc 和 clippy 进行代码质量检查
 */
public class RustCodeValidator {

    private static final Logger logger = LoggerFactory.getLogger(RustCodeValidator.class);

    // 验证超时设置（秒）
    private static final int RUSTC_TIMEOUT_SECONDS = 30;
    private static final int CLIPPY_TIMEOUT_SECONDS = 30;

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean success;
        private final List<String> errors;
        private final List<String> warnings;
        private final String fullOutput;

        public ValidationResult(boolean success, List<String> errors, List<String> warnings, String fullOutput) {
            this.success = success;
            this.errors = new ArrayList<>(errors);
            this.warnings = new ArrayList<>(warnings);
            this.fullOutput = fullOutput;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getFullOutput() {
            return fullOutput;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public int getErrorCount() {
            return errors.size();
        }

        public int getWarningCount() {
            return warnings.size();
        }
    }

    /**
     * 检查系统是否安装了 Rust 工具链
     *
     * @return true 如果 rustc 可用
     */
    public boolean isRustcAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("rustc", "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            logger.debug("rustc not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查系统是否安装了 Clippy
     *
     * @return true 如果 clippy 可用
     */
    public boolean isClippyAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cargo", "clippy", "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            logger.debug("clippy not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证 Rust 代码（编译检查）
     *
     * @param rustCode Rust 代码内容
     * @param tempFile 临时文件路径（用于编译检查）
     * @return 验证结果
     */
    public ValidationResult validateWithRustc(String rustCode, Path tempFile) {
        logger.info("Validating Rust code with rustc: {}", tempFile);

        try {
            // 1. 写入临时文件
            Files.writeString(tempFile, rustCode, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // 2. 执行 rustc --crate-type lib --check
            ProcessBuilder pb = new ProcessBuilder(
                "rustc",
                "--crate-type", "lib",
                "--check",
                tempFile.toAbsolutePath().toString()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 3. 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 4. 等待完成
            boolean finished = process.waitFor(RUSTC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("rustc validation timed out");
                return new ValidationResult(false,
                    List.of("Compilation check timed out after " + RUSTC_TIMEOUT_SECONDS + " seconds"),
                    List.of(),
                    output.toString());
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString();

            // 5. 解析输出
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            parseRustcOutput(outputStr, errors, warnings);

            boolean success = exitCode == 0 && errors.isEmpty();

            logger.info("rustc validation result: success={}, errors={}, warnings={}",
                success, errors.size(), warnings.size());

            return new ValidationResult(success, errors, warnings, outputStr);

        } catch (IOException e) {
            logger.error("Failed to write temp file for validation", e);
            return new ValidationResult(false,
                List.of("I/O error: " + e.getMessage()),
                List.of(),
                "");
        } catch (InterruptedException e) {
            logger.error("Validation interrupted", e);
            Thread.currentThread().interrupt();
            return new ValidationResult(false,
                List.of("Validation interrupted"),
                List.of(),
                "");
        }
    }

    /**
     * 使用 Clippy 检查代码风格和安全问题
     *
     * @param rustCode Rust 代码内容
     * @param tempFile 临时文件路径
     * @return 验证结果
     */
    public ValidationResult validateWithClippy(String rustCode, Path tempFile) {
        logger.info("Validating Rust code with clippy: {}", tempFile);

        try {
            // 1. 写入临时文件
            Files.writeString(tempFile, rustCode, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // 2. 执行 clippy
            // 注意：clippy 需要完整的 Cargo 项目，这里我们使用 rustc 的 -Z flag 或者简化处理
            // 为了简化，我们直接对单个文件运行 clippy-driver
            ProcessBuilder pb = new ProcessBuilder(
                "clippy-driver",
                "--crate-type", "lib",
                "-W", "clippy::all",
                tempFile.toAbsolutePath().toString()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 3. 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 4. 等待完成
            boolean finished = process.waitFor(CLIPPY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("clippy validation timed out");
                return new ValidationResult(false,
                    List.of("Clippy check timed out after " + CLIPPY_TIMEOUT_SECONDS + " seconds"),
                    List.of(),
                    output.toString());
            }

            String outputStr = output.toString();

            // 5. 解析输出
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            parseClippyOutput(outputStr, errors, warnings);

            // Clippy 的 exit code 可能不是 0 即使只有警告
            boolean success = errors.isEmpty();

            logger.info("clippy validation result: success={}, errors={}, warnings={}",
                success, errors.size(), warnings.size());

            return new ValidationResult(success, errors, warnings, outputStr);

        } catch (IOException e) {
            logger.error("Failed to run clippy validation", e);
            return new ValidationResult(false,
                List.of("I/O error: " + e.getMessage()),
                List.of(),
                "");
        } catch (InterruptedException e) {
            logger.error("Clippy validation interrupted", e);
            Thread.currentThread().interrupt();
            return new ValidationResult(false,
                List.of("Validation interrupted"),
                List.of(),
                "");
        }
    }

    /**
     * 解析 rustc 输出，提取错误和警告
     */
    private void parseRustcOutput(String output, List<String> errors, List<String> warnings) {
        if (output == null || output.isEmpty()) {
            return;
        }

        String[] lines = output.split("\n");
        for (String line : lines) {
            // rustc 的错误通常包含 "error:" 或 "error[E"
            if (line.contains("error:") || line.contains("error[E")) {
                errors.add(line.trim());
            }
            // rustc 的警告通常包含 "warning:"
            else if (line.contains("warning:")) {
                warnings.add(line.trim());
            }
        }
    }

    /**
     * 解析 Clippy 输出，提取错误和警告
     */
    private void parseClippyOutput(String output, List<String> errors, List<String> warnings) {
        if (output == null || output.isEmpty()) {
            return;
        }

        String[] lines = output.split("\n");
        for (String line : lines) {
            // Clippy 的错误和警告格式类似 rustc
            if (line.contains("error:") || line.contains("error[E")) {
                errors.add(line.trim());
            } else if (line.contains("warning:")) {
                warnings.add(line.trim());
            }
        }
    }

    /**
     * 清理临时文件
     */
    public void cleanupTempFile(Path tempFile) {
        try {
            if (Files.exists(tempFile)) {
                Files.delete(tempFile);
                logger.debug("Deleted temp file: {}", tempFile);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete temp file: {}", tempFile, e);
        }
    }
}
