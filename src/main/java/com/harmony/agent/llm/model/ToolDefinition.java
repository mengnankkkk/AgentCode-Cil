package com.harmony.agent.llm.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具定义 - 用于声明 AI 可用的工具
 *
 * 支持 OpenAI Function Calling 和 Claude Tool Use 的标准格式
 * 参数遵循 JSON Schema 标准
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;

    private ToolDefinition(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = builder.parameters;
    }

    // ==================== Getters ====================

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    // ==================== Builder ====================

    public static class Builder {
        private String name;
        private String description;
        private Map<String, Object> parameters = new HashMap<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 添加参数定义（JSON Schema 格式）
         * 例如：
         * addParameter("type", "object")
         * addParameter("properties", {...})
         * addParameter("required", List.of("path"))
         */
        public Builder addParameter(String key, Object value) {
            this.parameters.put(key, value);
            return this;
        }

        /**
         * 设置所有参数
         */
        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = new HashMap<>(parameters);
            return this;
        }

        public ToolDefinition build() {
            return new ToolDefinition(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format("ToolDefinition{name='%s', description='%s'}", name, description);
    }
}
