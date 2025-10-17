package com.harmony.agent.llm.role;

/**
 * Analyzer role - breaks down requirements into tasks
 * Uses smaller, faster models for efficient analysis
 */
public class AnalyzerRole extends BaseLLMRole {

    @Override
    public String getRoleName() {
        return "analyzer";
    }

    @Override
    public String getRoleDescription() {
        return "Analyzes requirements and breaks them down into actionable tasks";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are a requirement analyzer for a security analysis tool.

            Your responsibilities:
            1. Analyze user requirements carefully
            2. Break down requirements into clear, actionable tasks
            3. Generate a structured todo list with 3-7 tasks
            4. Each task should be specific and measurable
            5. Tasks should follow a logical sequence

            Output format:
            Return a numbered list of tasks, one per line:
            1. First task description
            2. Second task description
            ...

            Be concise but clear. Focus on practical, executable steps.
            """;
    }

    @Override
    public double getRecommendedTemperature() {
        return 0.3; // Low temperature for consistent, focused analysis
    }

    @Override
    public int getRecommendedMaxTokens() {
        return 1000; // Moderate token limit for task lists
    }
}
