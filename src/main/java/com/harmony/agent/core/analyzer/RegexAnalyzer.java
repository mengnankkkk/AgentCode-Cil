package com.harmony.agent.core.analyzer;

import com.harmony.agent.core.model.CodeLocation;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.SecurityIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in regex-based analyzer as fallback
 * Does not require external tools - uses pattern matching for common vulnerabilities
 */
public class RegexAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(RegexAnalyzer.class);

    private final List<SecurityPattern> patterns;

    public RegexAnalyzer() {
        this.patterns = buildSecurityPatterns();
    }

    @Override
    public String getName() {
        return "RegexAnalyzer";
    }

    @Override
    public String getVersion() {
        return "1.0.0-builtin";
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available as fallback
    }

    @Override
    public List<SecurityIssue> analyze(Path filePath) throws AnalyzerException {
        List<SecurityIssue> issues = new ArrayList<>();

        try {
            String content = Files.readString(filePath);
            String[] lines = content.split("\n");

            for (SecurityPattern pattern : patterns) {
                Matcher matcher = pattern.pattern.matcher(content);

                while (matcher.find()) {
                    int lineNumber = getLineNumber(content, matcher.start());
                    String snippet = lines[lineNumber - 1].trim();

                    CodeLocation location = new CodeLocation(
                        filePath.toString(),
                        lineNumber,
                        0,
                        snippet
                    );

                    SecurityIssue issue = new SecurityIssue.Builder()
                        .id(pattern.id + "-" + filePath.getFileName() + "-" + lineNumber)
                        .title(pattern.message)
                        .description(pattern.message)
                        .severity(pattern.severity)
                        .category(pattern.category)
                        .location(location)
                        .analyzer(getName())
                        .metadata("matched_code", matcher.group())
                        .build();

                    issues.add(issue);
                }
            }

            logger.debug("RegexAnalyzer found {} issues in {}", issues.size(), filePath);

        } catch (IOException e) {
            throw new AnalyzerException("Failed to read file: " + filePath, e);
        }

        return issues;
    }

    @Override
    public List<SecurityIssue> analyzeAll(List<Path> files) throws AnalyzerException {
        List<SecurityIssue> allIssues = new ArrayList<>();

        for (Path file : files) {
            allIssues.addAll(analyze(file));
        }

        return allIssues;
    }

    private int getLineNumber(String content, int position) {
        int line = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * Build list of security patterns to detect
     */
    private List<SecurityPattern> buildSecurityPatterns() {
        List<SecurityPattern> patterns = new ArrayList<>();

        // Buffer Overflow Patterns
        patterns.add(new SecurityPattern(
            "buffer-overflow-strcpy",
            Pattern.compile("\\bstrcpy\\s*\\([^)]+\\)"),
            "Unsafe use of strcpy() can lead to buffer overflow",
            IssueSeverity.HIGH,
            IssueCategory.BUFFER_OVERFLOW
        ));

        patterns.add(new SecurityPattern(
            "buffer-overflow-strcat",
            Pattern.compile("\\bstrcat\\s*\\([^)]+\\)"),
            "Unsafe use of strcat() can lead to buffer overflow",
            IssueSeverity.HIGH,
            IssueCategory.BUFFER_OVERFLOW
        ));

        patterns.add(new SecurityPattern(
            "buffer-overflow-sprintf",
            Pattern.compile("\\bsprintf\\s*\\([^)]+\\)"),
            "Unsafe use of sprintf() can lead to buffer overflow",
            IssueSeverity.HIGH,
            IssueCategory.BUFFER_OVERFLOW
        ));

        patterns.add(new SecurityPattern(
            "buffer-overflow-gets",
            Pattern.compile("\\bgets\\s*\\([^)]+\\)"),
            "gets() is inherently unsafe and should never be used",
            IssueSeverity.CRITICAL,
            IssueCategory.BUFFER_OVERFLOW
        ));

        // Memory Safety Patterns
        patterns.add(new SecurityPattern(
            "null-pointer-deref",
            Pattern.compile("\\*\\s*\\w+\\s*(?!;|\\))"),
            "Potential null pointer dereference",
            IssueSeverity.MEDIUM,
            IssueCategory.NULL_POINTER
        ));

        patterns.add(new SecurityPattern(
            "memory-leak-malloc",
            Pattern.compile("\\bmalloc\\s*\\([^)]+\\)"),
            "malloc() call detected - ensure proper free() is called",
            IssueSeverity.LOW,
            IssueCategory.MEMORY_LEAK
        ));

        // Format String Vulnerabilities
        patterns.add(new SecurityPattern(
            "format-string-printf",
            Pattern.compile("\\bprintf\\s*\\(\\s*[a-zA-Z_]\\w*\\s*\\)"),
            "Potential format string vulnerability - use printf(\"%s\", str)",
            IssueSeverity.HIGH,
            IssueCategory.FORMAT_STRING
        ));

        // Command Injection
        patterns.add(new SecurityPattern(
            "command-injection-system",
            Pattern.compile("\\bsystem\\s*\\([^\"'][^)]*\\)"),
            "Potential command injection via system() with variable input",
            IssueSeverity.CRITICAL,
            IssueCategory.COMMAND_INJECTION
        ));

        patterns.add(new SecurityPattern(
            "command-injection-popen",
            Pattern.compile("\\bpopen\\s*\\([^\"'][^)]*\\)"),
            "Potential command injection via popen() with variable input",
            IssueSeverity.CRITICAL,
            IssueCategory.COMMAND_INJECTION
        ));

        // Weak Cryptography
        patterns.add(new SecurityPattern(
            "weak-random-rand",
            Pattern.compile("\\brand\\s*\\(\\)"),
            "rand() is not cryptographically secure",
            IssueSeverity.MEDIUM,
            IssueCategory.WEAK_CRYPTO
        ));

        patterns.add(new SecurityPattern(
            "weak-crypto-md5",
            Pattern.compile("\\b(MD5|md5)\\b"),
            "MD5 is cryptographically broken - use SHA-256 or better",
            IssueSeverity.MEDIUM,
            IssueCategory.WEAK_CRYPTO
        ));

        // Race Conditions
        patterns.add(new SecurityPattern(
            "toctou-access-open",
            Pattern.compile("access\\s*\\([^)]+\\)[^;]*open\\s*\\("),
            "Potential TOCTOU race condition between access() and open()",
            IssueSeverity.MEDIUM,
            IssueCategory.RACE_CONDITION
        ));

        // Integer Overflow
        patterns.add(new SecurityPattern(
            "integer-overflow-multiply",
            Pattern.compile("\\*\\s*sizeof\\s*\\("),
            "Potential integer overflow in size calculation",
            IssueSeverity.MEDIUM,
            IssueCategory.INTEGER_OVERFLOW
        ));

        // Path Traversal
        patterns.add(new SecurityPattern(
            "path-traversal",
            Pattern.compile("\\.\\./"),
            "Potential path traversal vulnerability",
            IssueSeverity.HIGH,
            IssueCategory.PATH_TRAVERSAL
        ));

        return patterns;
    }

    /**
     * Represents a security pattern to match
     */
    private static class SecurityPattern {
        final String id;
        final Pattern pattern;
        final String message;
        final IssueSeverity severity;
        final IssueCategory category;

        SecurityPattern(String id, Pattern pattern, String message,
                       IssueSeverity severity, IssueCategory category) {
            this.id = id;
            this.pattern = pattern;
            this.message = message;
            this.severity = severity;
            this.category = category;
        }
    }
}
