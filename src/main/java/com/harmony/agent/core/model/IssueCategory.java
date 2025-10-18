package com.harmony.agent.core.model;

/**
 * Security issue categories for OpenHarmony
 */
public enum IssueCategory {
    // Memory Safety
    BUFFER_OVERFLOW("Buffer Overflow", "Memory"),
    USE_AFTER_FREE("Use After Free", "Memory"),
    MEMORY_LEAK("Memory Leak", "Memory"),
    NULL_DEREFERENCE("Null Pointer Dereference", "Memory"),
    NULL_POINTER("Null Pointer Dereference", "Memory"), // Alias
    DOUBLE_FREE("Double Free", "Memory"),

    // Concurrency
    RACE_CONDITION("Race Condition", "Concurrency"),
    DEADLOCK("Deadlock", "Concurrency"),
    THREAD_SAFETY("Thread Safety Issue", "Concurrency"),

    // Input Validation
    SQL_INJECTION("SQL Injection", "Injection"),
    COMMAND_INJECTION("Command Injection", "Injection"),
    PATH_TRAVERSAL("Path Traversal", "Injection"),
    FORMAT_STRING("Format String Vulnerability", "Injection"),

    // Cryptography
    WEAK_CRYPTO("Weak Cryptography", "Crypto"),
    HARDCODED_SECRET("Hardcoded Secret", "Crypto"),
    INSECURE_RANDOM("Insecure Random", "Crypto"),

    // Resource Management
    RESOURCE_LEAK("Resource Leak", "Resource"),
    FD_LEAK("File Descriptor Leak", "Resource"),

    // Code Quality
    CODE_SMELL("Code Smell", "Quality"),
    DEPRECATED_API("Deprecated API", "Quality"),
    INTEGER_OVERFLOW("Integer Overflow", "Quality"),
    CODE_QUALITY("Code Quality Issue", "Quality"),
    ERROR_HANDLING("Error Handling Issue", "Quality"),
    UNSAFE_CODE("Unsafe Code Usage", "Quality"),

    // Other
    UNDEFINED_BEHAVIOR("Undefined Behavior", "Other"),
    UNKNOWN("Unknown Issue", "Other");

    private final String displayName;
    private final String group;

    IssueCategory(String displayName, String group) {
        this.displayName = displayName;
        this.group = group;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGroup() {
        return group;
    }

    /**
     * Parse category from string (case-insensitive)
     */
    public static IssueCategory fromString(String value) {
        if (value == null || value.isEmpty()) {
            return UNKNOWN;
        }

        for (IssueCategory category : values()) {
            if (category.name().equalsIgnoreCase(value) ||
                category.displayName.equalsIgnoreCase(value)) {
                return category;
            }
        }

        return UNKNOWN;
    }
}
