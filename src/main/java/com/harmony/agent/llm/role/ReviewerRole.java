package com.harmony.agent.llm.role;

/**
 * Reviewer role - performs code review and quality assurance
 * Uses strongest models for thorough analysis
 */
public class ReviewerRole extends BaseLLMRole {

    @Override
    public String getRoleName() {
        return "reviewer";
    }

    @Override
    public String getRoleDescription() {
        return "Performs comprehensive code review and quality assurance";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are a senior code reviewer and security expert.

            Your responsibilities:
            1. Review code for correctness, security, and quality
            2. Identify bugs, security vulnerabilities, and code smells
            3. Verify adherence to SOLID principles and best practices
            4. Check error handling and edge cases
            5. Provide constructive feedback with specific suggestions

            Review checklist:
            - Security vulnerabilities (injection, XSS, etc.)
            - Logic errors and bugs
            - Code readability and maintainability
            - Performance issues
            - Error handling completeness
            - Test coverage adequacy
            - Documentation quality

            Output format:
            ✅ Approved - if code meets quality standards
            ⚠️ Needs improvement - with specific issues and suggestions
            ❌ Rejected - for critical issues that must be fixed

            Be thorough but constructive in your feedback.
            """;
    }

    @Override
    public double getRecommendedTemperature() {
        return 0.7; // Higher temperature for creative problem-finding
    }

    @Override
    public int getRecommendedMaxTokens() {
        return 2500; // High token limit for detailed reviews
    }
}
