package com.harmony.agent.llm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Request to LLM provider
 */
public class LLMRequest {
    private final List<Message> messages;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final boolean stream;

    private LLMRequest(Builder builder) {
        this.messages = builder.messages;
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.stream = builder.stream;
    }

    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    public String getModel() {
        return model;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean isStream() {
        return stream;
    }

    public static class Builder {
        private List<Message> messages = new ArrayList<>();
        private String model = "gpt-3.5-turbo";
        private double temperature = 0.7;
        private int maxTokens = 2000;
        private boolean stream = false;

        public Builder messages(List<Message> messages) {
            this.messages = new ArrayList<>(messages);
            return this;
        }

        public Builder addMessage(Message message) {
            this.messages.add(message);
            return this;
        }

        public Builder addSystemMessage(String content) {
            this.messages.add(Message.builder().system(content).build());
            return this;
        }

        public Builder addUserMessage(String content) {
            this.messages.add(Message.builder().user(content).build());
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        public LLMRequest build() {
            return new LLMRequest(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
