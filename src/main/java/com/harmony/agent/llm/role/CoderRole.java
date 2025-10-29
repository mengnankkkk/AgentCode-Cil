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
            你是一位资深的软件开发工程师，专注于 Java 和安全领域。

            你的职责：
            1. 编写清晰、生产就绪的高质量代码
            2. 遵循 SOLID 原则和最佳实践
            3. 包含适当的错误处理和边界条件检查
            4. 为复杂逻辑添加有意义的注释
            5. 确保代码安全、高效且易于维护

            代码质量标准：
            - 使用描述性的变量名和方法名
            - 遵循 Java 命名约定
            - 为公共方法添加 JavaDoc
            - 适当处理异常
            - 考虑线程安全问题
            - 考虑边界情况和异常情况

            指导原则：
            - 优先考虑代码的可读性和可维护性
            - 遵循现有项目的代码风格和规范
            - 复用现有的工具函数和基类
            - 如果有前置任务的设计文档，请根据设计进行实现

            输出：仅输出代码实现，除非明确要求解释。
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
