package com.harmony.agent.core.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a security issue found during analysis
 */
public class SecurityIssue {
    private final String id;
    private final String title;
    private final String description;
    private final IssueSeverity severity;
    private final IssueCategory category;
    private final CodeLocation location;
    private final String analyzer;
    private final Map<String, Object> metadata;

    private SecurityIssue(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.description = builder.description;
        this.severity = builder.severity;
        this.category = builder.category;
        this.location = builder.location;
        this.analyzer = builder.analyzer;
        this.metadata = builder.metadata;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public IssueCategory getCategory() {
        return category;
    }

    public CodeLocation getLocation() {
        return location;
    }

    public String getAnalyzer() {
        return analyzer;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Generate unique hash for deduplication
     */
    public String getHash() {
        return String.format("%s:%s:%d:%d",
            category.name(),
            location.getFilePath(),
            location.getLineNumber(),
            location.getColumnNumber()
        );
    }

    @Override
    public String toString() {
        return String.format("[%s] %s at %s",
            severity.getDisplayName(),
            title,
            location
        );
    }

    /**
     * Builder for SecurityIssue
     */
    public static class Builder {
        private String id;
        private String title;
        private String description;
        private IssueSeverity severity = IssueSeverity.INFO;
        private IssueCategory category = IssueCategory.UNKNOWN;
        private CodeLocation location;
        private String analyzer;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder severity(IssueSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder category(IssueCategory category) {
            this.category = category;
            return this;
        }

        public Builder location(CodeLocation location) {
            this.location = location;
            return this;
        }

        public Builder analyzer(String analyzer) {
            this.analyzer = analyzer;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public SecurityIssue build() {
            if (title == null || title.isEmpty()) {
                throw new IllegalStateException("SecurityIssue must have a title");
            }
            if (location == null) {
                throw new IllegalStateException("SecurityIssue must have a location");
            }
            return new SecurityIssue(this);
        }
    }
}
