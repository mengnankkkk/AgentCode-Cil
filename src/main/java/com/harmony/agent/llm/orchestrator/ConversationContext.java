package com.harmony.agent.llm.orchestrator;

import com.harmony.agent.task.TodoList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversation context for managing state across LLM role interactions
 * Enables information sharing between Analyzer, Planner, Coder, and Reviewer
 */
public class ConversationContext {

    private final String requirement;
    private TodoList todoList;
    private String designDocument;
    private final Map<String, String> generatedCode = new HashMap<>();
    private final List<ReviewComment> reviewComments = new ArrayList<>();
    private final Map<String, Object> metadata = new HashMap<>();

    public ConversationContext(String requirement) {
        this.requirement = requirement;
    }

    // Getters and Setters

    public String getRequirement() {
        return requirement;
    }

    public TodoList getTodoList() {
        return todoList;
    }

    public void setTodoList(TodoList todoList) {
        this.todoList = todoList;
    }

    public String getDesignDocument() {
        return designDocument;
    }

    public void setDesignDocument(String designDocument) {
        this.designDocument = designDocument;
    }

    public void addGeneratedCode(String fileName, String code) {
        generatedCode.put(fileName, code);
    }

    public String getGeneratedCode(String fileName) {
        return generatedCode.get(fileName);
    }

    public Map<String, String> getAllGeneratedCode() {
        return new HashMap<>(generatedCode);
    }

    public void addReviewComment(ReviewComment comment) {
        reviewComments.add(comment);
    }

    public List<ReviewComment> getReviewComments() {
        return new ArrayList<>(reviewComments);
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Build context string for role execution
     */
    public String buildContextString() {
        StringBuilder context = new StringBuilder();

        context.append("# Requirement\n");
        context.append(requirement).append("\n\n");

        if (todoList != null) {
            context.append("# Task Breakdown\n");
            context.append("Total tasks: ").append(todoList.getTotalTaskCount()).append("\n");
            context.append("Completed: ").append(todoList.getCompletedTaskCount()).append("\n\n");
        }

        if (designDocument != null && !designDocument.isEmpty()) {
            context.append("# Design Document\n");
            context.append(designDocument).append("\n\n");
        }

        if (!generatedCode.isEmpty()) {
            context.append("# Generated Code\n");
            context.append("Files: ").append(String.join(", ", generatedCode.keySet())).append("\n\n");
        }

        if (!reviewComments.isEmpty()) {
            context.append("# Review Comments\n");
            context.append("Total comments: ").append(reviewComments.size()).append("\n");
            reviewComments.forEach(comment ->
                context.append("- [").append(comment.severity).append("] ").append(comment.message).append("\n")
            );
            context.append("\n");
        }

        return context.toString();
    }

    /**
     * Review comment class
     */
    public static class ReviewComment {
        public enum Severity {
            CRITICAL, HIGH, MEDIUM, LOW, INFO
        }

        private final Severity severity;
        private final String message;
        private final String fileName;
        private final Integer lineNumber;

        public ReviewComment(Severity severity, String message, String fileName, Integer lineNumber) {
            this.severity = severity;
            this.message = message;
            this.fileName = fileName;
            this.lineNumber = lineNumber;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }

        public String getFileName() {
            return fileName;
        }

        public Integer getLineNumber() {
            return lineNumber;
        }

        @Override
        public String toString() {
            String location = fileName != null ? fileName : "general";
            if (lineNumber != null) {
                location += ":" + lineNumber;
            }
            return String.format("[%s] %s (%s)", severity, message, location);
        }
    }
}
