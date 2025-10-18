package com.harmony.agent.llm.role;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating LLM roles
 */
public class RoleFactory {

    private final Map<String, LLMRole> roles = new HashMap<>();

    /**
     * Register a role
     */
    public void registerRole(String name, LLMRole role) {
        roles.put(name.toLowerCase(), role);
    }

    /**
     * Get a role by name
     */
    public LLMRole getRole(String name) {
        LLMRole role = roles.get(name.toLowerCase());
        if (role == null) {
            throw new IllegalArgumentException("Role not found: " + name);
        }
        return role;
    }

    /**
     * Check if role exists
     */
    public boolean hasRole(String name) {
        return roles.containsKey(name.toLowerCase());
    }

    /**
     * Create default factory with all standard roles
     */
    public static RoleFactory createDefault() {
        RoleFactory factory = new RoleFactory();

        factory.registerRole("analyzer", new AnalyzerRole());
        factory.registerRole("planner", new PlannerRole());
        factory.registerRole("coder", new CoderRole());
        factory.registerRole("reviewer", new ReviewerRole());
        // Note: Tester is NOT an LLM role - it's a tool executor

        return factory;
    }
}
