package com.harmony.agent.tools.result;

import java.util.ArrayList;
import java.util.List;

/**
 * JUnit测试结果
 */
public class TestResult {
    private final boolean success;
    private final String output;
    private final int testsRun;
    private final int testsPassed;
    private final int testsFailed;
    private final int testsSkipped;
    private final List<TestFailure> failures;
    private final int exitCode;
    private final long durationMs;

    public TestResult(boolean success, String output, int testsRun, int testsPassed,
                     int testsFailed, int testsSkipped, List<TestFailure> failures,
                     int exitCode, long durationMs) {
        this.success = success;
        this.output = output;
        this.testsRun = testsRun;
        this.testsPassed = testsPassed;
        this.testsFailed = testsFailed;
        this.testsSkipped = testsSkipped;
        this.failures = failures != null ? failures : new ArrayList<>();
        this.exitCode = exitCode;
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public int getTestsRun() {
        return testsRun;
    }

    public int getTestsPassed() {
        return testsPassed;
    }

    public int getTestsFailed() {
        return testsFailed;
    }

    public int getTestsSkipped() {
        return testsSkipped;
    }

    public List<TestFailure> getFailures() {
        return failures;
    }

    public int getExitCode() {
        return exitCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    /**
     * 测试失败详情
     */
    public static class TestFailure {
        private final String testClass;
        private final String testMethod;
        private final String message;
        private final String stackTrace;

        public TestFailure(String testClass, String testMethod, String message, String stackTrace) {
            this.testClass = testClass;
            this.testMethod = testMethod;
            this.message = message;
            this.stackTrace = stackTrace;
        }

        public String getTestClass() {
            return testClass;
        }

        public String getTestMethod() {
            return testMethod;
        }

        public String getMessage() {
            return message;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        @Override
        public String toString() {
            return String.format("%s.%s: %s", testClass, testMethod, message);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Test Result: ").append(success ? "SUCCESS" : "FAILURE").append("\n");
        sb.append("Tests Run: ").append(testsRun).append("\n");
        sb.append("Passed: ").append(testsPassed).append(", Failed: ").append(testsFailed)
          .append(", Skipped: ").append(testsSkipped).append("\n");
        sb.append("Duration: ").append(durationMs).append("ms\n");
        if (hasFailures()) {
            sb.append("Failures:\n");
            for (TestFailure failure : failures) {
                sb.append("  ").append(failure).append("\n");
            }
        }
        return sb.toString();
    }
}
