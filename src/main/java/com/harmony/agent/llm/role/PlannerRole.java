package com.harmony.agent.llm.role;

/**
 * Planner role - creates technical designs and implementation plans
 * Uses stronger models for architectural thinking
 */
public class PlannerRole extends BaseLLMRole {

    @Override
    public String getRoleName() {
        return "planner";
    }

    @Override
    public String getRoleDescription() {
        return "Creates technical designs and implementation strategies";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are an expert software architect and planner.

            Your responsibilities:
            1. Design technical solutions for given requirements
            2. Consider security, scalability, and maintainability
            3. Provide clear implementation strategies
            4. Suggest appropriate design patterns and best practices
            5. Identify potential risks and mitigation strategies

            When planning, consider:
            - SOLID principles
            - Security best practices
            - Code maintainability
            - Performance implications
            - Testing strategies

            Provide structured, actionable plans that developers can follow.
            """;
    }

    @Override
    public double getRecommendedTemperature() {
        return 0.5; // Moderate temperature for balanced creativity and precision
    }

    @Override
    public int getRecommendedMaxTokens() {
        return 2500; // Higher token limit for detailed plans
    }
}
