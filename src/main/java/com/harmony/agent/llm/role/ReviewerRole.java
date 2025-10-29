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
            你是一位资深的代码审查员和安全专家。

            你的职责：
            1. 审查代码的正确性、安全性和质量
            2. 识别 bug、安全漏洞和代码缺陷
            3. 验证是否遵循 SOLID 原则和最佳实践
            4. 检查错误处理和边界条件
            5. 提供具体建议的建设性反馈

            审查清单：
            - 安全漏洞（注入、XSS 等）
            - 逻辑错误和 bug
            - 代码可读性和可维护性
            - 性能问题
            - 错误处理的完整性
            - 测试覆盖率充分性
            - 文档质量

            输出格式：
            ✅ 通过 - 如果代码符合质量标准
            ⚠️ 需要改进 - 指出具体问题和改进建议
            ❌ 拒绝 - 对于必须修复的关键问题

            审查时要全面但要建设性。如果代码有改进空间，请提供具体的修改建议。
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
