package com.harmony.agent.core.ai;

import com.harmony.agent.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test AI filtering logic - verifies that false positives are completely removed
 */
class DecisionEngineFilterTest {

    private DecisionEngine decisionEngine;
    private CachedAiValidationClient mockAiClient;
    private CodeSlicer mockCodeSlicer;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        mockAiClient = mock(CachedAiValidationClient.class);
        mockCodeSlicer = mock(CodeSlicer.class);
        executorService = Executors.newFixedThreadPool(2);

        decisionEngine = new DecisionEngine(mockAiClient, mockCodeSlicer, executorService, 2);
    }

    @Test
    void testFalsePositivesAreCompletelyFiltered() throws Exception {
        // Create test issues
        SecurityIssue issue1 = createTestIssue("ISSUE-001", "Potential vulnerability", IssueSeverity.HIGH);
        SecurityIssue issue2 = createTestIssue("ISSUE-002", "False positive", IssueSeverity.MEDIUM);
        SecurityIssue issue3 = createTestIssue("ISSUE-003", "Real vulnerability", IssueSeverity.CRITICAL);

        List<SecurityIssue> inputIssues = new ArrayList<>();
        inputIssues.add(issue1);  // Will be validated as TRUE
        inputIssues.add(issue2);  // Will be filtered as FALSE
        inputIssues.add(issue3);  // Will be validated as TRUE

        // Mock code slicer
        when(mockCodeSlicer.getContextSlice(any(), anyInt()))
            .thenReturn("mock code context");

        // Mock AI responses
        // Issue 1: AI confirms it's a vulnerability
        when(mockAiClient.sendRequest(contains("ISSUE-001"), anyBoolean()))
            .thenReturn("{\"is_vulnerability\": true, \"reason\": \"Real issue\", \"suggested_severity\": \"HIGH\"}");

        // Issue 2: AI marks as false positive
        when(mockAiClient.sendRequest(contains("ISSUE-002"), anyBoolean()))
            .thenReturn("{\"is_vulnerability\": false, \"reason\": \"Not exploitable\", \"suggested_severity\": \"INFO\"}");

        // Issue 3: AI confirms it's a vulnerability
        when(mockAiClient.sendRequest(contains("ISSUE-003"), anyBoolean()))
            .thenReturn("{\"is_vulnerability\": true, \"reason\": \"Critical bug\", \"suggested_severity\": \"CRITICAL\"}");

        when(mockAiClient.isAvailable()).thenReturn(true);

        // Execute AI enhancement
        List<SecurityIssue> enhancedIssues = decisionEngine.enhanceIssues(inputIssues);

        // ✅ CRITICAL ASSERTION: Only 2 issues should remain (issue2 filtered out)
        assertEquals(2, enhancedIssues.size(),
            "AI should filter out false positives completely - only 2 issues should remain");

        // Verify the correct issues remain
        assertTrue(enhancedIssues.stream().anyMatch(i -> i.getId().equals("ISSUE-001")),
            "ISSUE-001 (validated) should be in final list");

        assertFalse(enhancedIssues.stream().anyMatch(i -> i.getId().equals("ISSUE-002")),
            "ISSUE-002 (false positive) should NOT be in final list");

        assertTrue(enhancedIssues.stream().anyMatch(i -> i.getId().equals("ISSUE-003")),
            "ISSUE-003 (validated) should be in final list");

        // Verify none of the remaining issues have ai_filtered=true
        for (SecurityIssue issue : enhancedIssues) {
            Object aiFiltered = issue.getMetadata().get("ai_filtered");
            assertFalse(aiFiltered != null && (Boolean) aiFiltered,
                "No issue with ai_filtered=true should be in final list");
        }

        System.out.println("✅ AI filtering test passed!");
        System.out.println("   Input: 3 issues");
        System.out.println("   Output: " + enhancedIssues.size() + " issues");
        System.out.println("   Filtered: 1 issue (ISSUE-002)");
    }

    @Test
    void testAllFalsePositivesFiltered() throws Exception {
        // Create test issues - all false positives
        SecurityIssue issue1 = createTestIssue("FP-001", "False alarm 1", IssueSeverity.HIGH);
        SecurityIssue issue2 = createTestIssue("FP-002", "False alarm 2", IssueSeverity.MEDIUM);

        List<SecurityIssue> inputIssues = new ArrayList<>();
        inputIssues.add(issue1);
        inputIssues.add(issue2);

        when(mockCodeSlicer.getContextSlice(any(), anyInt()))
            .thenReturn("mock code context");

        // Both marked as false positives
        when(mockAiClient.sendRequest(anyString(), anyBoolean()))
            .thenReturn("{\"is_vulnerability\": false, \"reason\": \"Not exploitable\", \"suggested_severity\": \"INFO\"}");

        when(mockAiClient.isAvailable()).thenReturn(true);

        // Execute AI enhancement
        List<SecurityIssue> enhancedIssues = decisionEngine.enhanceIssues(inputIssues);

        // ✅ CRITICAL ASSERTION: Result should be empty (all filtered)
        assertEquals(0, enhancedIssues.size(),
            "When all issues are false positives, result should be empty list");

        System.out.println("✅ All false positives filtered correctly!");
        System.out.println("   Input: 2 issues (all false positives)");
        System.out.println("   Output: 0 issues (all filtered)");
    }

    private SecurityIssue createTestIssue(String id, String title, IssueSeverity severity) {
        return new SecurityIssue.Builder()
            .id(id)
            .title(title)
            .description("Test issue description")
            .severity(severity)
            .category(IssueCategory.BUFFER_OVERFLOW)
            .location(new CodeLocation("test.c", 100))
            .analyzer("SemgrepAnalyzer")  // Needs AI validation
            .build();
    }
}
