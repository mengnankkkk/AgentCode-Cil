package com.harmony.agent.tools.result;

import java.util.ArrayList;
import java.util.List;

/**
 * SpotBugs静态分析结果
 */
public class AnalysisResult {
    private final boolean success;
    private final String output;
    private final List<Bug> bugs;
    private final int exitCode;
    private final long durationMs;

    public AnalysisResult(boolean success, String output, List<Bug> bugs, int exitCode, long durationMs) {
        this.success = success;
        this.output = output;
        this.bugs = bugs != null ? bugs : new ArrayList<>();
        this.exitCode = exitCode;
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public List<Bug> getBugs() {
        return bugs;
    }

    public int getExitCode() {
        return exitCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean hasBugs() {
        return !bugs.isEmpty();
    }

    public int getBugCount() {
        return bugs.size();
    }

    public long getHighPriorityCount() {
        return bugs.stream().filter(b -> "High".equalsIgnoreCase(b.priority)).count();
    }

    public long getMediumPriorityCount() {
        return bugs.stream().filter(b -> "Medium".equalsIgnoreCase(b.priority)).count();
    }

    public long getLowPriorityCount() {
        return bugs.stream().filter(b -> "Low".equalsIgnoreCase(b.priority)).count();
    }

    /**
     * Bug详情
     */
    public static class Bug {
        private final String file;
        private final int line;
        private final String type;
        private final String category;
        private final String priority; // High, Medium, Low
        private final String message;

        public Bug(String file, int line, String type, String category, String priority, String message) {
            this.file = file;
            this.line = line;
            this.type = type;
            this.category = category;
            this.priority = priority;
            this.message = message;
        }

        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public String getType() {
            return type;
        }

        public String getCategory() {
            return category;
        }

        public String getPriority() {
            return priority;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("[%s:%s] %s:%d - %s", priority, category, file, line, message);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Analysis Result: ").append(success ? "SUCCESS" : "FAILURE").append("\n");
        sb.append("Total Bugs: ").append(getBugCount()).append("\n");
        sb.append("High: ").append(getHighPriorityCount())
          .append(", Medium: ").append(getMediumPriorityCount())
          .append(", Low: ").append(getLowPriorityCount()).append("\n");
        sb.append("Duration: ").append(durationMs).append("ms\n");
        if (hasBugs()) {
            sb.append("Bugs:\n");
            for (Bug bug : bugs) {
                sb.append("  ").append(bug).append("\n");
            }
        }
        return sb.toString();
    }
}
