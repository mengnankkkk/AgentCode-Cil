package com.harmony.agent.llm.model;

/**
 * Response from LLM provider
 */
public class LLMResponse {
    private final String content;
    private final String model;
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final boolean success;
    private final String errorMessage;

    private LLMResponse(Builder builder) {
        this.content = builder.content;
        this.model = builder.model;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.totalTokens = builder.totalTokens;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static class Builder {
        private String content;
        private String model;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private boolean success = true;
        private String errorMessage;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder promptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder totalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            this.success = false;
            return this;
        }

        public LLMResponse build() {
            return new LLMResponse(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
