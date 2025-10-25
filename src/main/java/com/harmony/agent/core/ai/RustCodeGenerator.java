package com.harmony.agent.core.ai;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.provider.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rust Code Generator - Generates production-ready Rust code from C/C++
 * Implements GVI (Generate-Verify-Iterate) loop with quality metrics
 */
public class RustCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(RustCodeGenerator.class);

    private static final double TEMPERATURE = 0.3;  // Lower temperature for more deterministic code
    private static final int MAX_TOKENS = 4000;
    private static final int MAX_ITERATIONS = 3;  // GVI loop iterations

    private final LLMProvider llmProvider;
    private final CodeSlicer codeSlicer;
    private final String model;

    /**
     * Generated Rust code result with quality metrics
     */
    public static class RustCodeResult {
        private final String rustCode;
        private final String originalCCode;
        private final int qualityScore;
        private final double unsafePercentage;
        private final int iterationCount;
        private final List<String> issues;
        private final List<String> improvements;

        public RustCodeResult(String rustCode, String originalCCode, int qualityScore,
                            double unsafePercentage, int iterationCount,
                            List<String> issues, List<String> improvements) {
            this.rustCode = rustCode;
            this.originalCCode = originalCCode;
            this.qualityScore = qualityScore;
            this.unsafePercentage = unsafePercentage;
            this.iterationCount = iterationCount;
            this.issues = issues;
            this.improvements = improvements;
        }

        public String getRustCode() { return rustCode; }
        public String getOriginalCCode() { return originalCCode; }
        public int getQualityScore() { return qualityScore; }
        public double getUnsafePercentage() { return unsafePercentage; }
        public int getIterationCount() { return iterationCount; }
        public List<String> getIssues() { return issues; }
        public List<String> getImprovements() { return improvements; }

        public boolean meetsQualityTarget() {
            return qualityScore >= 90 && unsafePercentage < 5.0;
        }
    }

    public RustCodeGenerator(LLMProvider llmProvider, CodeSlicer codeSlicer, String model) {
        this.llmProvider = llmProvider;
        this.codeSlicer = codeSlicer;
        this.model = model;
        logger.info("RustCodeGenerator initialized with provider: {}, model: {}",
            llmProvider.getProviderName(), model);
    }

    /**
     * Generate Rust code from C/C++ source file using GVI loop
     */
    public RustCodeResult generateRustCode(Path sourceFile) throws IOException {
        logger.info("Starting Rust code generation for: {}", sourceFile);

        // Read the entire C file
        String cCode = Files.readString(sourceFile);
        
        return generateRustCodeFromString(cCode, sourceFile.getFileName().toString());
    }

    /**
     * Generate Rust code from C/C++ code string using GVI loop
     */
    public RustCodeResult generateRustCodeFromString(String cCode, String fileName) {
        logger.info("Generating Rust code from C source (length: {})", cCode.length());

        String currentRustCode = null;
        List<String> allIssues = new ArrayList<>();
        List<String> allImprovements = new ArrayList<>();
        int iteration = 0;

        // GVI Loop: Generate -> Verify -> Iterate
        for (iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            logger.info("GVI Loop - Iteration {}/{}", iteration, MAX_ITERATIONS);

            // GENERATE: Generate or refine Rust code
            String prompt = buildGenerationPrompt(cCode, fileName, currentRustCode, allIssues, iteration);
            currentRustCode = callLLMForGeneration(prompt);

            if (currentRustCode == null || currentRustCode.isEmpty()) {
                logger.error("LLM returned empty Rust code");
                break;
            }

            // VERIFY: Check quality metrics
            int qualityScore = calculateQualityScore(currentRustCode, cCode);
            double unsafePercentage = calculateUnsafePercentage(currentRustCode);
            List<String> currentIssues = identifyIssues(currentRustCode);

            logger.info("Quality Score: {}/100, Unsafe: {}%, Issues: {}",
                qualityScore, String.format("%.1f", unsafePercentage), currentIssues.size());

            allIssues = currentIssues;

            // Check if quality targets are met
            if (qualityScore >= 90 && unsafePercentage < 5.0 && currentIssues.isEmpty()) {
                logger.info("Quality targets met in iteration {}", iteration);
                allImprovements.add("Iteration " + iteration + ": Quality target achieved (Score: " + 
                    qualityScore + ", Unsafe: " + String.format("%.1f%%", unsafePercentage) + ")");
                break;
            }

            // ITERATE: If not last iteration, prepare for refinement
            if (iteration < MAX_ITERATIONS) {
                allImprovements.add("Iteration " + iteration + ": Generated code (Score: " + 
                    qualityScore + ", Unsafe: " + String.format("%.1f%%", unsafePercentage) + 
                    ", Issues: " + currentIssues.size() + ")");
            }
        }

        // Final quality assessment
        int finalQualityScore = calculateQualityScore(currentRustCode, cCode);
        double finalUnsafePercentage = calculateUnsafePercentage(currentRustCode);

        return new RustCodeResult(
            currentRustCode,
            cCode,
            finalQualityScore,
            finalUnsafePercentage,
            iteration,
            allIssues,
            allImprovements
        );
    }

    /**
     * Build prompt for Rust code generation
     */
    private String buildGenerationPrompt(String cCode, String fileName, 
                                        String previousRustCode, List<String> issues, 
                                        int iteration) {
        StringBuilder prompt = new StringBuilder();

        if (iteration == 1) {
            // First iteration: fresh generation
            prompt.append("You are an expert Rust developer specializing in C-to-Rust migration.\n\n");
            prompt.append("Task: Convert the following C code to safe, idiomatic Rust code.\n\n");
            prompt.append("Requirements:\n");
            prompt.append("1. Generate COMPLETE, production-ready Rust code\n");
            prompt.append("2. Target quality score: >= 90/100\n");
            prompt.append("3. Minimize unsafe code: < 5% (ideally 0%)\n");
            prompt.append("4. Use Rust idioms: ownership, borrowing, Result/Option, iterators\n");
            prompt.append("5. Add clear comments explaining safety guarantees\n");
            prompt.append("6. Include all necessary imports and module structure\n");
            prompt.append("7. Preserve all functionality from the C code\n");
            prompt.append("8. Replace manual memory management with Rust's ownership system\n\n");
        } else {
            // Subsequent iterations: refinement
            prompt.append("You are refining Rust code to meet quality targets.\n\n");
            prompt.append("Previous Rust code had the following issues:\n");
            for (String issue : issues) {
                prompt.append("- ").append(issue).append("\n");
            }
            prompt.append("\n");
            prompt.append("Task: Improve the Rust code to address these issues.\n\n");
            prompt.append("Requirements:\n");
            prompt.append("1. Fix all identified issues\n");
            prompt.append("2. Improve quality score to >= 90/100\n");
            prompt.append("3. Reduce unsafe code to < 5% (ideally 0%)\n");
            prompt.append("4. Maintain all functionality\n\n");
        }

        prompt.append("Original C code (").append(fileName).append("):\n");
        prompt.append("```c\n");
        prompt.append(cCode);
        prompt.append("\n```\n\n");

        if (previousRustCode != null && iteration > 1) {
            prompt.append("Previous Rust code (to be improved):\n");
            prompt.append("```rust\n");
            prompt.append(previousRustCode);
            prompt.append("\n```\n\n");
        }

        prompt.append("Output ONLY the complete Rust code in a single code block, no explanations.\n");
        prompt.append("Format: ```rust\\n<code>\\n```\n");

        return prompt.toString();
    }

    /**
     * Call LLM to generate Rust code
     */
    private String callLLMForGeneration(String prompt) {
        try {
            LLMRequest request = LLMRequest.builder()
                .model(model)
                .temperature(TEMPERATURE)
                .maxTokens(MAX_TOKENS)
                .addSystemMessage(
                    "You are an expert Rust developer. Generate safe, idiomatic, production-ready Rust code. " +
                    "Minimize or eliminate unsafe blocks. Use Result/Option for error handling. " +
                    "Output only code in ```rust code blocks, no explanations."
                )
                .addUserMessage(prompt)
                .build();

            LLMResponse response = llmProvider.sendRequest(request);

            if (!response.isSuccess()) {
                logger.error("LLM request failed: {}", response.getErrorMessage());
                return null;
            }

            String content = response.getContent();
            if (content == null || content.trim().isEmpty()) {
                logger.error("LLM returned empty response");
                return null;
            }

            // Extract Rust code from markdown code block
            String rustCode = extractRustCode(content);
            
            logger.info("Generated Rust code ({} chars, {} tokens used)",
                rustCode.length(), response.getTotalTokens());

            return rustCode;

        } catch (Exception e) {
            logger.error("Failed to generate Rust code", e);
            return null;
        }
    }

    /**
     * Extract Rust code from markdown code block
     */
    private String extractRustCode(String content) {
        // Try to find ```rust code block
        Pattern pattern = Pattern.compile("```rust\\s*\\n(.+?)\\n```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Try generic ``` code block
        pattern = Pattern.compile("```\\s*\\n(.+?)\\n```", Pattern.DOTALL);
        matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // If no code block, return as-is
        return content.trim();
    }

    /**
     * Calculate quality score (0-100) for generated Rust code
     * 
     * Scoring factors:
     * - Proper error handling (Result/Option): 25 points
     * - No unsafe blocks: 25 points
     * - Idiomatic Rust patterns: 20 points
     * - Complete functionality: 15 points
     * - Documentation/comments: 10 points
     * - Compiler-friendly syntax: 5 points
     */
    private int calculateQualityScore(String rustCode, String originalCCode) {
        int score = 0;

        // 1. Error handling (Result/Option) - 25 points
        if (rustCode.contains("Result<") || rustCode.contains("Option<")) {
            score += 20;
        }
        if (rustCode.contains("?") || rustCode.contains(".unwrap_or") || 
            rustCode.contains("match")) {
            score += 5;
        }

        // 2. No unsafe blocks - 25 points
        long unsafeCount = countPattern(rustCode, "unsafe");
        if (unsafeCount == 0) {
            score += 25;
        } else if (unsafeCount <= 2) {
            score += 15;
        } else if (unsafeCount <= 5) {
            score += 5;
        }

        // 3. Idiomatic Rust patterns - 20 points
        int patternCount = 0;
        if (rustCode.contains("impl ")) patternCount++;
        if (rustCode.contains("pub fn ")) patternCount++;
        if (rustCode.contains("Vec<") || rustCode.contains("&[") || rustCode.contains("&mut ")) patternCount++;
        if (rustCode.contains(".iter()") || rustCode.contains(".collect()")) patternCount++;
        if (rustCode.contains("&self") || rustCode.contains("&mut self")) patternCount++;
        score += Math.min(20, patternCount * 4);

        // 4. Complete functionality - 15 points
        // Check if main functions from C are present in Rust
        long cFunctionCount = countPattern(originalCCode, "\\b\\w+\\s*\\([^)]*\\)\\s*\\{");
        long rustFunctionCount = countPattern(rustCode, "fn \\w+");
        if (rustFunctionCount >= cFunctionCount * 0.8) {
            score += 15;
        } else if (rustFunctionCount >= cFunctionCount * 0.5) {
            score += 10;
        } else {
            score += 5;
        }

        // 5. Documentation/comments - 10 points
        long commentLines = rustCode.lines().filter(l -> l.trim().startsWith("//")).count();
        if (commentLines >= 5) {
            score += 10;
        } else if (commentLines >= 3) {
            score += 7;
        } else if (commentLines >= 1) {
            score += 4;
        }

        // 6. Compiler-friendly syntax - 5 points
        if (rustCode.contains("use ") || rustCode.contains("mod ")) score += 2;
        if (rustCode.contains("fn main()")) score += 3;

        return Math.min(100, score);
    }

    /**
     * Calculate percentage of unsafe code
     */
    private double calculateUnsafePercentage(String rustCode) {
        if (rustCode == null || rustCode.isEmpty()) {
            return 0.0;
        }

        long totalLines = rustCode.lines().filter(l -> !l.trim().isEmpty()).count();
        if (totalLines == 0) {
            return 0.0;
        }

        // Count unsafe blocks
        long unsafeCount = countPattern(rustCode, "unsafe\\s*\\{");
        
        // Estimate lines in unsafe blocks (rough approximation)
        long unsafeLines = unsafeCount * 3; // Assume average 3 lines per unsafe block

        return (double) unsafeLines / totalLines * 100.0;
    }

    /**
     * Identify issues in generated Rust code
     */
    private List<String> identifyIssues(String rustCode) {
        List<String> issues = new ArrayList<>();

        // Check for excessive unsafe usage
        long unsafeCount = countPattern(rustCode, "unsafe");
        if (unsafeCount > 0) {
            issues.add("Contains " + unsafeCount + " unsafe block(s) - target is 0");
        }

        // Check for missing error handling
        if (!rustCode.contains("Result<") && !rustCode.contains("Option<")) {
            issues.add("Missing proper error handling (Result/Option)");
        }

        // Check for unwrap() usage (can panic)
        long unwrapCount = countPattern(rustCode, "\\.unwrap\\(\\)");
        if (unwrapCount > 2) {
            issues.add("Excessive use of .unwrap() (" + unwrapCount + " occurrences) - use ? or match instead");
        }

        // Check for missing module structure
        if (!rustCode.contains("use ") && rustCode.length() > 100) {
            issues.add("Missing import statements - may not compile");
        }

        // Check for potential memory issues
        if (rustCode.contains("Box::from_raw") || rustCode.contains("mem::transmute")) {
            issues.add("Uses unsafe memory operations - consider safer alternatives");
        }

        return issues;
    }

    /**
     * Count pattern occurrences using regex
     */
    private long countPattern(String text, String pattern) {
        if (text == null || pattern == null) {
            return 0;
        }
        
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(text);
            long count = 0;
            while (m.find()) {
                count++;
            }
            return count;
        } catch (Exception e) {
            logger.warn("Failed to count pattern: {}", pattern, e);
            return 0;
        }
    }

    /**
     * Check if LLM provider is available
     */
    public boolean isAvailable() {
        return llmProvider.isAvailable();
    }

    /**
     * Get provider name
     */
    public String getProviderName() {
        return llmProvider.getProviderName();
    }

    /**
     * Get model name
     */
    public String getModelName() {
        return model;
    }
}
