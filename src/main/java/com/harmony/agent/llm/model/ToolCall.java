package com.harmony.agent.llm.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具调用 - 表示 AI 对工具的一次调用请求
 *
 * 当 AI 需要执行某个工具时，会返回这样的结构
 */
public class ToolCall {

    private final String id;               // 工具调用 ID（唯一标识）
    private final String name;             // 工具名称
    private final Map<String, Object> arguments;  // 调用参数

    private ToolCall(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.arguments = builder.arguments;
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    /**
     * 获取指定参数的值
     * @param key 参数名
     * @return 参数值，如果不存在返回 null
     */
    public Object getArgument(String key) {
        return arguments.get(key);
    }

    /**
     * 获取指定参数的字符串值
     * @param key 参数名
     * @return 参数值，如果不存在或不是字符串返回 null
     */
    public String getStringArgument(String key) {
        Object value = arguments.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    // ==================== Builder ====================

    public static class Builder {
        private String id;
        private String name;
        private Map<String, Object> arguments = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 添加单个参数
         */
        public Builder addArgument(String key, Object value) {
            this.arguments.put(key, value);
            return this;
        }

        /**
         * 设置所有参数
         */
        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = new HashMap<>(arguments);
            return this;
        }

        public ToolCall build() {
            return new ToolCall(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format("ToolCall{id='%s', name='%s', arguments=%s}", id, name, arguments);
    }
}
