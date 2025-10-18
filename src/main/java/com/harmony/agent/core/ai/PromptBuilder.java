package com.harmony.agent.core.ai;

import com.harmony.agent.core.model.SecurityIssue;

/**
 * Prompt Builder - Constructs high-quality prompts for AI analysis
 * Centralized prompt management for consistent AI interactions
 */
public class PromptBuilder {

    /**
     * Build prompt for AI vulnerability validation
     * Analyzes whether a static analysis finding is a real vulnerability or false positive
     *
     * @param issue The security issue to validate
     * @param codeSlice The code context around the issue
     * @return Formatted prompt for LLM
     */
    public static String buildIssueValidationPrompt(SecurityIssue issue, String codeSlice) {
        return String.format("""
            You are a C/C++ static analysis and security expert.
            A tool (%s) found a *potential* security issue:

            - Issue: %s
            - Description: %s
            - File: %s:%d
            - Severity (Reported): %s
            - Category: %s

            Below is the code context (the entire function) where the issue was found:
            ```c
            %s
            ```

            Analyze this context carefully. Is this a *real, exploitable vulnerability*, or is it likely a *false positive*?

            Consider:
            - Buffer sizes and bounds checks
            - Null pointer checks
            - Data flow and taint analysis
            - Input validation
            - Error handling
            - Context-specific mitigations

            Example 1 (Real Vulnerability):
            {
              "is_vulnerability": true,
              "reason": "Buffer overflow: strcpy without bounds check on user input from untrusted source",
              "suggested_severity": "Critical"
            }

            Example 2 (False Positive):
            {
              "is_vulnerability": false,
              "reason": "Input is validated and size-limited (line 15) before strcpy call on line 18",
              "suggested_severity": "Info"
            }

            Now analyze this case and respond ONLY in the following JSON format:
            {
              "is_vulnerability": true/false,
              "reason": "Your detailed technical explanation here...",
              "suggested_severity": "Critical/High/Medium/Low/Info"
            }
            """,
            issue.getAnalyzer(),
            issue.getTitle(),
            issue.getDescription(),
            issue.getLocation().getFilePath(),
            issue.getLocation().getLineNumber(),
            issue.getSeverity().getDisplayName(),
            issue.getCategory().name(),
            codeSlice
        );
    }

    /**
     * Build prompt for Rust FFI migration analysis
     * Provides guidance on migrating C code to Rust with FFI
     *
     * @param codeSlice The C code to analyze for migration
     * @return Formatted prompt for LLM
     */
    public static String buildRustFFIPrompt(String codeSlice) {
        return String.format("""
            You are an expert in C-to-Rust migration and FFI (Foreign Function Interface).
            Analyze the following C function for potential Rust migration:

            ```c
            %s
            ```

            Provide a detailed migration analysis:

            ## 1. Core Responsibility
            What is the primary purpose of this C function?

            ## 2. FFI Safety Wrapper
            Provide an `extern "C"` Rust function that acts as a *safe wrapper* around this C code:
            - Use `#[no_mangle]`
            - Use proper C types (`std::ffi::CString`, `std::ffi::CStr`, raw pointers)
            - Check all pointers for null
            - Handle C error codes appropriately

            ## 3. Rust-native Rewrite
            Provide an *idiomatic, safe Rust* function that *replaces* the C code's logic:
            - Use Rust's ownership model
            - Use `Result` and `Option` for error handling
            - Use safe Rust types (`String`, `Vec`, etc.)
            - Follow Rust best practices

            ## 4. Key Challenges

            ### Error Handling
            - How are C error codes (return values, errno) mapped to Rust `Result` or `Option`?
            - What error types should be defined?

            ### Memory Management
            - How do C's manual `malloc`/`free` map to Rust's ownership?
            - Which types should be used? (`Vec<u8>`, `String`, `Box`, etc.)
            - Who owns the memory in FFI boundaries?

            ### Type Mapping
            - How do C types map to Rust types?
            - C `char*` → Rust ?
            - C `void*` → Rust ?
            - C structs → Rust ?

            ### Performance Considerations
            - FFI call overhead
            - Zero-copy strategies
            - Allocation patterns

            ## 5. Migration Strategy
            Recommend whether to:
            - Keep in C with FFI wrapper
            - Fully rewrite in Rust
            - Hybrid approach

            Structure your response clearly using Markdown with code blocks.
            """,
            codeSlice
        );
    }

    /**
     * Build prompt for code quality analysis
     * General code review and improvement suggestions
     *
     * @param codeSlice The code to analyze
     * @return Formatted prompt for LLM
     */
    public static String buildCodeQualityPrompt(String codeSlice) {
        return String.format("""
            You are a code quality expert specializing in C/C++.
            Analyze the following code for quality issues:

            ```c
            %s
            ```

            Provide analysis in these areas:

            ## Security
            - Memory safety issues
            - Input validation
            - Error handling

            ## Maintainability
            - Code complexity
            - Naming conventions
            - Documentation

            ## Performance
            - Algorithmic efficiency
            - Resource usage
            - Potential optimizations

            For each issue, provide:
            - Severity (Critical/High/Medium/Low/Info)
            - Description
            - Suggested fix

            Respond in JSON format:
            {
              "issues": [
                {
                  "category": "Security/Maintainability/Performance",
                  "severity": "High",
                  "description": "...",
                  "suggested_fix": "..."
                }
              ],
              "overall_quality_score": 0-100
            }
            """,
            codeSlice
        );
    }

    /**
     * Build generic analysis prompt
     * Flexible prompt for custom analysis tasks
     *
     * @param task The analysis task description
     * @param context The code or data to analyze
     * @return Formatted prompt for LLM
     */
    public static String buildGenericAnalysisPrompt(String task, String context) {
        return String.format("""
            Task: %s

            Context:
            ```
            %s
            ```

            Please provide a detailed analysis addressing the task above.
            Use clear, technical language and provide specific examples where applicable.
            """,
            task,
            context
        );
    }
}
