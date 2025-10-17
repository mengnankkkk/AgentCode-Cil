package com.harmony.agent.llm.model;

/**
 * Represents a single message in LLM conversation
 */
public class Message {
    private final MessageRole role;
    private final String content;

    public Message(MessageRole role, String content) {
        this.role = role;
        this.content = content;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", role, content);
    }

    /**
     * Message role enum
     */
    public enum MessageRole {
        SYSTEM,
        USER,
        ASSISTANT
    }

    // Builder pattern for convenience
    public static class Builder {
        private MessageRole role;
        private String content;

        public Builder role(MessageRole role) {
            this.role = role;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder system(String content) {
            this.role = MessageRole.SYSTEM;
            this.content = content;
            return this;
        }

        public Builder user(String content) {
            this.role = MessageRole.USER;
            this.content = content;
            return this;
        }

        public Builder assistant(String content) {
            this.role = MessageRole.ASSISTANT;
            this.content = content;
            return this;
        }

        public Message build() {
            return new Message(role, content);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
