package com.harmony.agent.core.model;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the result of a security scan
 */
public class ScanResult {
    private final String scanId;
    private final String sourcePath;
    private final Instant startTime;
    private final Instant endTime;
    private final List<SecurityIssue> issues;
    private final Map<String, Object> statistics;
    private final List<String> analyzersUsed;

    private ScanResult(Builder builder) {
        this.scanId = builder.scanId;
        this.sourcePath = builder.sourcePath;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.issues = new ArrayList<>(builder.issues);
        this.statistics = new HashMap<>(builder.statistics);
        this.analyzersUsed = new ArrayList<>(builder.analyzersUsed);
    }

    public String getScanId() {
        return scanId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }

    public List<SecurityIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public Map<String, Object> getStatistics() {
        return Collections.unmodifiableMap(statistics);
    }

    public List<String> getAnalyzersUsed() {
        return Collections.unmodifiableList(analyzersUsed);
    }

    /**
     * Get issues filtered by severity
     */
    public List<SecurityIssue> getIssuesBySeverity(IssueSeverity severity) {
        return issues.stream()
            .filter(issue -> issue.getSeverity() == severity)
            .collect(Collectors.toList());
    }

    /**
     * Get issues filtered by category
     */
    public List<SecurityIssue> getIssuesByCategory(IssueCategory category) {
        return issues.stream()
            .filter(issue -> issue.getCategory() == category)
            .collect(Collectors.toList());
    }

    /**
     * Get total issue count
     */
    public int getTotalIssueCount() {
        return issues.size();
    }

    /**
     * Get issue count by severity
     */
    public Map<IssueSeverity, Long> getIssueCountBySeverity() {
        return issues.stream()
            .collect(Collectors.groupingBy(
                SecurityIssue::getSeverity,
                Collectors.counting()
            ));
    }

    /**
     * Get issue count by category
     */
    public Map<IssueCategory, Long> getIssueCountByCategory() {
        return issues.stream()
            .collect(Collectors.groupingBy(
                SecurityIssue::getCategory,
                Collectors.counting()
            ));
    }

    /**
     * Check if scan has critical issues
     */
    public boolean hasCriticalIssues() {
        return issues.stream()
            .anyMatch(issue -> issue.getSeverity() == IssueSeverity.CRITICAL);
    }

    @Override
    public String toString() {
        return String.format("ScanResult[id=%s, issues=%d, duration=%s]",
            scanId,
            getTotalIssueCount(),
            getDuration()
        );
    }

    /**
     * Builder for ScanResult
     */
    public static class Builder {
        private String scanId = UUID.randomUUID().toString();
        private String sourcePath;
        private Instant startTime = Instant.now();
        private Instant endTime;
        private List<SecurityIssue> issues = new ArrayList<>();
        private Map<String, Object> statistics = new HashMap<>();
        private List<String> analyzersUsed = new ArrayList<>();

        public Builder scanId(String scanId) {
            this.scanId = scanId;
            return this;
        }

        public Builder sourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder addIssue(SecurityIssue issue) {
            this.issues.add(issue);
            return this;
        }

        public Builder addIssues(Collection<SecurityIssue> issues) {
            this.issues.addAll(issues);
            return this;
        }

        public Builder addStatistic(String key, Object value) {
            this.statistics.put(key, value);
            return this;
        }

        public Builder addAnalyzer(String analyzer) {
            this.analyzersUsed.add(analyzer);
            return this;
        }

        public ScanResult build() {
            if (sourcePath == null || sourcePath.isEmpty()) {
                throw new IllegalStateException("ScanResult must have a source path");
            }
            if (endTime == null) {
                endTime = Instant.now();
            }
            return new ScanResult(this);
        }
    }
}
