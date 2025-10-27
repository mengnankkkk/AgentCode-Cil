package com.harmony.agent.tools.result;

/**
 * 脚本执行结果 - 用于 Bash、Shell 脚本执行
 */
public class ScriptResult {
    private final boolean success;
    private final String output;
    private final String error;
    private final int exitCode;
    private final long durationMs;

    public ScriptResult(boolean success, String output, String error, int exitCode, long durationMs) {
        this.success = success;
        this.output = output;
        this.error = error;
        this.exitCode = exitCode;
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public int getExitCode() {
        return exitCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Script Result: ").append(success ? "SUCCESS" : "FAILURE").append("\n");
        sb.append("Exit Code: ").append(exitCode).append("\n");
        sb.append("Duration: ").append(durationMs).append("ms\n");
        if (!output.isEmpty()) {
            sb.append("Output:\n").append(output).append("\n");
        }
        if (!error.isEmpty()) {
            sb.append("Error:\n").append(error).append("\n");
        }
        return sb.toString();
    }
}
