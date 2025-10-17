package com.harmony.agent.core.model;

/**
 * Security issue severity levels
 */
public enum IssueSeverity {
    CRITICAL("Critical", 4),
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1),
    INFO("Info", 0);

    private final String displayName;
    private final int level;

    IssueSeverity(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Parse severity from string (case-insensitive)
     */
    public static IssueSeverity fromString(String value) {
        if (value == null || value.isEmpty()) {
            return INFO;
        }

        for (IssueSeverity severity : values()) {
            if (severity.name().equalsIgnoreCase(value) ||
                severity.displayName.equalsIgnoreCase(value)) {
                return severity;
            }
        }

        return INFO;
    }
}
