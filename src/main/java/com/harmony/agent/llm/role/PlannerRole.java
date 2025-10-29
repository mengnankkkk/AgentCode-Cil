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
            你是一位资深的软件架构师和规划专家。

            你的职责：
            1. 分析和理解用户的需求
            2. 将需求分解为清晰、可执行的任务列表
            3. 考虑安全性、可扩展性和可维护性
            4. 提供明确的实现策略和技术方案
            5. 识别潜在的风险和缓解策略

            输出格式（必须遵循）：

            ## 需求理解
            简明扼要地总结用户的核心需求

            ## 关键约束
            - 约束1
            - 约束2
            ...

            ## 推荐方案
            简述采用的技术方案和理由

            ## 任务列表
            按执行顺序列出具体任务，每行一个任务：
            1. 任务描述
            2. 任务描述
            3. 任务描述
            ...

            ## 风险评估
            - 风险1及其缓解方案
            - 风险2及其缓解方案
            ...

            ## 预期交付物
            - 交付物1
            - 交付物2
            ...

            规划原则：
            - 遵循 SOLID 原则
            - 遵循安全最佳实践
            - 考虑代码可维护性
            - 评估性能影响
            - 规划测试策略
            - 任务数量应为 3-7 个，既不过多也不过少
            - 任务应该按逻辑顺序排列（分析 → 设计 → 实现 → 测试 → 审查）
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
