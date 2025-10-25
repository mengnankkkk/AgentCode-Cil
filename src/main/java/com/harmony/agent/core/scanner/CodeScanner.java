package com.harmony.agent.core.scanner;

import com.harmony.agent.core.model.ProjectType;
import com.harmony.agent.core.parser.CompileCommandsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Code file scanner with support for filtering and incremental scanning
 * Supports C/C++, Java, and Rust projects based on project type detection
 */
public class CodeScanner {

    private static final Logger logger = LoggerFactory.getLogger(CodeScanner.class);

    // Supported C/C++ file extensions
    private static final Set<String> C_CPP_EXTENSIONS = Set.of(
        ".c", ".cpp", ".cc", ".cxx", ".h", ".hpp", ".hxx"
    );

    // Supported Java file extensions
    private static final Set<String> JAVA_EXTENSIONS = Set.of(
        ".java"
    );

    // Supported Rust file extensions
    private static final Set<String> RUST_EXTENSIONS = Set.of(
        ".rs"
    );

    // Default ignore patterns
    private static final Set<String> DEFAULT_IGNORE_PATTERNS = Set.of(
        "node_modules", "build", "dist", "target", ".git", ".svn",
        "*.o", "*.obj", "*.so", "*.dll", "*.dylib", "*.a", "*.lib"
    );

    private final Path basePath;
    private final Set<String> ignorePatterns;
    private final boolean useGitignore;
    private final CompileCommandsParser compileCommandsParser;
    private final ProjectType projectType;
    private final Set<String> supportedExtensions;

    public CodeScanner(String basePath) {
        this(basePath, true, null);
    }

    public CodeScanner(String basePath, boolean useGitignore) {
        this(basePath, useGitignore, null);
    }

    public CodeScanner(String basePath, boolean useGitignore, String compileCommandsPath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        this.ignorePatterns = new HashSet<>(DEFAULT_IGNORE_PATTERNS);
        this.useGitignore = useGitignore;

        // Detect project type based on directory contents
        this.projectType = ProjectType.detectFromDirectory(this.basePath.toFile());
        logger.info("Detected project type: {}", this.projectType.getDisplayName());

        // Set supported extensions based on project type
        this.supportedExtensions = switch (this.projectType) {
            case C_CPP -> C_CPP_EXTENSIONS;
            case JAVA -> JAVA_EXTENSIONS;
            case RUST -> RUST_EXTENSIONS;
            default -> C_CPP_EXTENSIONS; // Default to C/C++ for unknown types
        };
        logger.info("Using file extensions: {}", this.supportedExtensions);

        // Initialize compile_commands parser if provided
        if (compileCommandsPath != null) {
            try {
                this.compileCommandsParser = new CompileCommandsParser(Paths.get(compileCommandsPath));
                logger.info("Using compile_commands.json for file discovery");
            } catch (IOException e) {
                logger.error("Failed to load compile_commands.json: {}", e.getMessage());
                throw new RuntimeException("Failed to load compile_commands.json", e);
            }
        } else {
            this.compileCommandsParser = null;
        }

        if (useGitignore && compileCommandsParser == null) {
            loadGitignorePatterns();
        }

        logger.info("CodeScanner initialized for: {} ({})", this.basePath, this.projectType.getDisplayName());
    }

    /**
     * Scan for all source files based on detected project type
     */
    public List<Path> scanAll() throws IOException {
        // Prioritize compile_commands.json if available (C/C++ projects)
        if (compileCommandsParser != null && projectType == ProjectType.C_CPP) {
            logger.info("Using compile_commands.json for file discovery");
            Set<Path> sourceFiles = compileCommandsParser.getSourceFiles();
            List<Path> result = new ArrayList<>(sourceFiles);
            logger.info("Found {} {} files from compile_commands.json", result.size(), projectType.getDisplayName());
            return result;
        }

        // Fall back to filesystem scan
        logger.info("Starting filesystem scan of: {} (looking for {} files)", basePath, projectType.getDisplayName());

        if (!Files.exists(basePath)) {
            throw new IOException("Path does not exist: " + basePath);
        }

        List<Path> files = new ArrayList<>();

        Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (shouldIgnore(dir)) {
                    logger.debug("Ignoring directory: {}", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isSupportedFile(file) && !shouldIgnore(file)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                logger.warn("Failed to visit file: {}", file, exc);
                return FileVisitResult.CONTINUE;
            }
        });

        logger.info("Found {} {} files", files.size(), projectType.getDisplayName());
        return files;
    }

    /**
     * Scan for changed files only (incremental scan using git)
     */
    public List<Path> scanIncremental() throws IOException {
        logger.info("Starting incremental scan of: {} ({})", basePath, projectType.getDisplayName());

        if (!isGitRepository()) {
            logger.warn("Not a git repository, falling back to full scan");
            return scanAll();
        }

        try {
            Set<Path> changedFiles = getGitChangedFiles();
            List<Path> result = changedFiles.stream()
                .filter(this::isSupportedFile)
                .filter(path -> !shouldIgnore(path))
                .collect(Collectors.toList());

            logger.info("Found {} changed {} files", result.size(), projectType.getDisplayName());
            return result;
        } catch (IOException e) {
            logger.error("Failed to get git changes, falling back to full scan", e);
            return scanAll();
        }
    }

    /**
     * Check if file is a supported file based on project type
     */
    private boolean isSupportedFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return supportedExtensions.stream()
            .anyMatch(fileName::endsWith);
    }

    /**
     * Check if path should be ignored based on patterns
     */
    private boolean shouldIgnore(Path path) {
        String relativePath = basePath.relativize(path).toString();
        String fileName = path.getFileName().toString();

        for (String pattern : ignorePatterns) {
            if (matchesPattern(relativePath, pattern) ||
                matchesPattern(fileName, pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Simple pattern matching (supports wildcards)
     */
    private boolean matchesPattern(String path, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");

        return Pattern.compile(regex).matcher(path).matches() ||
               path.contains(pattern);
    }

    /**
     * Load .gitignore patterns
     */
    private void loadGitignorePatterns() {
        Path gitignorePath = basePath.resolve(".gitignore");

        if (!Files.exists(gitignorePath)) {
            logger.debug("No .gitignore file found");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(gitignorePath);
            for (String line : lines) {
                line = line.trim();
                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                ignorePatterns.add(line);
            }
            logger.info("Loaded {} patterns from .gitignore", lines.size());
        } catch (IOException e) {
            logger.warn("Failed to load .gitignore", e);
        }
    }

    /**
     * Check if directory is a git repository
     */
    private boolean isGitRepository() {
        return Files.exists(basePath.resolve(".git"));
    }

    /**
     * Get changed files from git
     */
    private Set<Path> getGitChangedFiles() throws IOException {
        Set<Path> changedFiles = new HashSet<>();

        // Get unstaged changes
        changedFiles.addAll(executeGitCommand("git", "diff", "--name-only"));

        // Get staged changes
        changedFiles.addAll(executeGitCommand("git", "diff", "--cached", "--name-only"));

        // Get untracked files
        changedFiles.addAll(executeGitCommand("git", "ls-files", "--others", "--exclude-standard"));

        return changedFiles;
    }

    /**
     * Execute git command and return file paths
     */
    private Set<Path> executeGitCommand(String... command) throws IOException {
        Set<Path> files = new HashSet<>();

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(basePath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        Path file = basePath.resolve(line);
                        if (Files.exists(file)) {
                            files.add(file);
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Git command failed with exit code: " + exitCode);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command interrupted", e);
        }

        return files;
    }

    /**
     * Add custom ignore pattern
     */
    public void addIgnorePattern(String pattern) {
        ignorePatterns.add(pattern);
    }

    /**
     * Get base path
     */
    public Path getBasePath() {
        return basePath;
    }

    /**
     * Get detected project type
     */
    public ProjectType getProjectType() {
        return projectType;
    }

    /**
     * Get statistics about the codebase
     */
    public ScanStatistics getStatistics() throws IOException {
        List<Path> allFiles = scanAll();

        Map<String, Long> extensionCount = allFiles.stream()
            .collect(Collectors.groupingBy(
                path -> getFileExtension(path),
                Collectors.counting()
            ));

        long totalLines = allFiles.stream()
            .mapToLong(this::countLines)
            .sum();

        return new ScanStatistics(
            allFiles.size(),
            totalLines,
            extensionCount
        );
    }

    private String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex) : "";
    }

    private long countLines(Path path) {
        try {
            return Files.lines(path).count();
        } catch (IOException e) {
            logger.warn("Failed to count lines in: {}", path, e);
            return 0;
        }
    }

    /**
     * Scan statistics
     */
    public static class ScanStatistics {
        private final int totalFiles;
        private final long totalLines;
        private final Map<String, Long> extensionCount;

        public ScanStatistics(int totalFiles, long totalLines, Map<String, Long> extensionCount) {
            this.totalFiles = totalFiles;
            this.totalLines = totalLines;
            this.extensionCount = extensionCount;
        }

        public int getTotalFiles() {
            return totalFiles;
        }

        public long getTotalLines() {
            return totalLines;
        }

        public Map<String, Long> getExtensionCount() {
            return extensionCount;
        }

        @Override
        public String toString() {
            return String.format("ScanStatistics[files=%d, lines=%d, extensions=%s]",
                totalFiles, totalLines, extensionCount);
        }
    }
}
