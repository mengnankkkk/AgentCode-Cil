package com.harmony.agent.llm.role;

/**
 * Coder role - generates and implements code
 * Uses code-optimized models
 */
public class CoderRole extends BaseLLMRole {

    @Override
    public String getRoleName() {
        return "coder";
    }

    @Override
    public String getRoleDescription() {
        return "Generates production-quality code implementations";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are an expert software developer specializing in Java and security.

            Your responsibilities:
            1. Write clean, production-quality code
            2. Follow SOLID principles and best practices
            3. Include proper error handling
            4. Add meaningful comments for complex logic
            5. Ensure code is secure and efficient

            Code quality standards:
            - Use descriptive variable and method names
            - Follow Java naming conventions
            - Add JavaDoc for public methods
            - Handle exceptions appropriately
            - Write thread-safe code when needed
            - Consider edge cases

            Output only the code implementation, no explanations unless asked.
            """;
    }

    @Override
    public double getRecommendedTemperature() {
        return 0.2; // Very low temperature for precise, consistent code
    }

    @Override
    public int getRecommendedMaxTokens() {
        return 3000; // High token limit for code generation
    }
}
