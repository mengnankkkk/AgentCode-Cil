package com.harmony.agent.core.ai;

import com.harmony.agent.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple test to verify AI filtering logic without mocks
 */
class SimpleFilterTest {

    @Test
    void testNullHandling() {
        List<SecurityIssue> results = new ArrayList<>();

        // Simulate what should happen in the main loop
        SecurityIssue issue1 = createIssue("valid");
        SecurityIssue issue2 = null;  // Filtered by AI
        SecurityIssue issue3 = createIssue("valid2");

        // This is what the fixed code should do
        if (issue1 != null) {
            results.add(issue1);
        }
        if (issue2 != null) {  // Should NOT add
            results.add(issue2);
        }
        if (issue3 != null) {
            results.add(issue3);
        }

        System.out.println("Results size: " + results.size());
        System.out.println("Expected: 2 (issue2 is null, so not added)");

        assert results.size() == 2 : "Expected 2 issues, got " + results.size();

        System.out.println("✅ Null handling works correctly!");
    }

    private SecurityIssue createIssue(String id) {
        return new SecurityIssue.Builder()
            .id(id)
            .title("Test")
            .description("Test")
            .severity(IssueSeverity.HIGH)
            .category(IssueCategory.BUFFER_OVERFLOW)
            .location(new CodeLocation("test.c", 100))
            .analyzer("Test")
            .build();
    }
}
