package com.harmony.agent.autofix;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of code review by Reviewer role
 */
public class ReviewResult {

    private final boolean passed;
    private final String reason;
    private final List<String> issues;

    public ReviewResult(boolean passed, String reason, List<String> issues) {
        this.passed = passed;
        this.reason = reason;
        this.issues = issues != null ? issues : new ArrayList<>();
    }

    public static ReviewResult pass(String reason) {
        return new ReviewResult(true, reason, new ArrayList<>());
    }

    public static ReviewResult fail(String reason, List<String> issues) {
        return new ReviewResult(false, reason, issues);
    }

    public boolean isPassed() { return passed; }
    public String getReason() { return reason; }
    public List<String> getIssues() { return issues; }

    @Override
    public String toString() {
        if (passed) {
            return "✓ PASS: " + reason;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("✗ FAIL: ").append(reason).append("\n");
            if (!issues.isEmpty()) {
                sb.append("Issues:\n");
                for (String issue : issues) {
                    sb.append("  - ").append(issue).append("\n");
                }
            }
            return sb.toString();
        }
    }
}
