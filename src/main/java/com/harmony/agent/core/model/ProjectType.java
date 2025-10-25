package com.harmony.agent.core.model;

/**
 * Project type enumeration
 * Used to determine which compilation and validation tools to use
 */
public enum ProjectType {

    /**
     * C/C++ project
     * - Compiler: Clang/GCC
     * - Build system: Make, CMake, compile_commands.json
     * - Analyzer: Semgrep, Cppcheck
     */
    C_CPP("C/C++", new String[]{".c", ".cpp", ".cc", ".cxx", ".h", ".hpp"}),

    /**
     * Java project
     * - Compiler: javac
     * - Build system: Maven, Gradle
     * - Analyzer: SpotBugs, PMD
     */
    JAVA("Java", new String[]{".java"}),

    /**
     * Rust project
     * - Compiler: rustc
     * - Build system: Cargo
     * - Analyzer: Clippy, Geiger (unsafe scanner)
     */
    RUST("Rust", new String[]{".rs"}),

    /**
     * Unknown/unsupported project type
     */
    UNKNOWN("Unknown", new String[]{});

    private final String displayName;
    private final String[] extensions;

    ProjectType(String displayName, String[] extensions) {
        this.displayName = displayName;
        this.extensions = extensions;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String[] getExtensions() {
        return extensions;
    }

    /**
     * Detect project type from file extension
     */
    public static ProjectType fromFile(String filePath) {
        if (filePath == null) {
            return UNKNOWN;
        }

        String lowerPath = filePath.toLowerCase();

        for (String ext : C_CPP.extensions) {
            if (lowerPath.endsWith(ext)) {
                return C_CPP;
            }
        }

        for (String ext : JAVA.extensions) {
            if (lowerPath.endsWith(ext)) {
                return JAVA;
            }
        }

        for (String ext : RUST.extensions) {
            if (lowerPath.endsWith(ext)) {
                return RUST;
            }
        }

        return UNKNOWN;
    }

    /**
     * Detect project type from directory
     * Looks for characteristic files (pom.xml, compile_commands.json, etc.)
     * Priority order:
     * 1. compile_commands.json (C/C++ - most explicit)
     * 2. Cargo.toml (Rust - most explicit)
     * 3. pom.xml/build.gradle (Java)
     * 4. CMakeLists.txt/Makefile (C/C++ fallback)
     */
    public static ProjectType detectFromDirectory(java.io.File directory) {
        if (directory == null || !directory.isDirectory()) {
            return UNKNOWN;
        }

        // Priority 1: Check for compile_commands.json (C/C++ - most explicit indicator)
        if (new java.io.File(directory, "compile_commands.json").exists()) {
            return C_CPP;
        }

        // Priority 2: Check for Rust project indicators
        if (new java.io.File(directory, "Cargo.toml").exists()) {
            return RUST;
        }

        // Priority 3: Check for Java project indicators
        if (new java.io.File(directory, "pom.xml").exists() ||
            new java.io.File(directory, "build.gradle").exists()) {
            return JAVA;
        }

        // Priority 4: Check for other C/C++ project indicators (fallback)
        if (new java.io.File(directory, "CMakeLists.txt").exists() ||
            new java.io.File(directory, "Makefile").exists()) {
            return C_CPP;
        }

        return UNKNOWN;
    }
}
