package com.harmony.agent.tools.result;

import java.util.ArrayList;
import java.util.List;

/**
 * Maven编译结果
 */
public class CompileResult {
    private final boolean success;
    private final String output;
    private final List<CompileError> errors;
    private final int exitCode;
    private final long durationMs;

    public CompileResult(boolean success, String output, List<CompileError> errors, int exitCode, long durationMs) {
        this.success = success;
        this.output = output;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.exitCode = exitCode;
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public List<CompileError> getErrors() {
        return errors;
    }

    public int getExitCode() {
        return exitCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int getErrorCount() {
        return errors.size();
    }

    /**
     * 编译错误详情
     */
    public static class CompileError {
        private final String file;
        private final int line;
        private final int column;
        private final String message;
        private final String severity; // ERROR, WARNING

        public CompileError(String file, int line, int column, String message, String severity) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.message = message;
            this.severity = severity;
        }

        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        public String getMessage() {
            return message;
        }

        public String getSeverity() {
            return severity;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s:%d:%d - %s", severity, file, line, column, message);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Compile Result: ").append(success ? "SUCCESS" : "FAILURE").append("\n");
        sb.append("Exit Code: ").append(exitCode).append("\n");
        sb.append("Duration: ").append(durationMs).append("ms\n");
        if (hasErrors()) {
            sb.append("Errors (").append(getErrorCount()).append("):\n");
            for (CompileError error : errors) {
                sb.append("  ").append(error).append("\n");
            }
        }
        return sb.toString();
    }
}
