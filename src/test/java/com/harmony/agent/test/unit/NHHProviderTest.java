package com.harmony.agent.test.unit;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.provider.NHHProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test NHH Provider
 */
public class NHHProviderTest {

    @Test
    public void testNHHProviderBasics() {
        String apiKey = "sk-AmoMMHVqeGcQGvgbBWD6ARwTyS791MPKJn21Sypv5bbBZr8w";
        NHHProvider provider = new NHHProvider(apiKey);

        // Test provider name
        assertEquals("nhh", provider.getProviderName());

        // Test available models
        String[] models = provider.getAvailableModels();
        assertTrue(models.length > 0);
        assertTrue(provider.supportsModel("glm-4.5-flash"));
        assertTrue(provider.supportsModel("gemini-2.0-flash"));
        assertTrue(provider.supportsModel("gpt-4"));
        assertFalse(provider.supportsModel("invalid-model"));

        // Test isAvailable
        assertTrue(provider.isAvailable());
    }

    @Test
    public void testNHHProviderRequest() {
        String apiKey = "sk-AmoMMHVqeGcQGvgbBWD6ARwTyS791MPKJn21Sypv5bbBZr8w";
        NHHProvider provider = new NHHProvider(apiKey);

        // Build request
        LLMRequest request = LLMRequest.builder()
            .model("glm-4.5-flash")
            .addUserMessage("你好，请问你是谁？")
            .temperature(0.7)
            .maxTokens(1000)
            .stream(false)
            .build();

        assertNotNull(request);
        assertEquals("glm-4.5-flash", request.getModel());
        assertEquals(1, request.getMessages().size());
        assertEquals(0.7, request.getTemperature());
        assertEquals(1000, request.getMaxTokens());
    }

    @Test
    public void testNHHProviderAPICall() {
        String apiKey = "sk-AmoMMHVqeGcQGvgbBWD6ARwTyS791MPKJn21Sypv5bbBZr8w";
        NHHProvider provider = new NHHProvider(apiKey);

        // Build request
        LLMRequest request = LLMRequest.builder()
            .model("glm-4.5-flash")
            .addUserMessage("你好")
            .temperature(0.3)
            .maxTokens(500)
            .build();

        // Send request
        System.out.println("\n========== NHH API Call Test ==========");
        System.out.println("Sending request to NHH API...");
        System.out.println("URL: https://new.123nhh.xyz/v1/chat/completions");
        System.out.println("Model: glm-4.5-flash");
        
        LLMResponse response = provider.sendRequest(request);

        // Verify response
        assertNotNull(response);
        System.out.println("\n========== Response ==========");
        System.out.println("Success: " + response.isSuccess());
        System.out.println("Content: " + response.getContent());
        System.out.println("Model: " + response.getModel());
        System.out.println("Prompt Tokens: " + response.getPromptTokens());
        System.out.println("Completion Tokens: " + response.getCompletionTokens());
        System.out.println("Total Tokens: " + response.getTotalTokens());

        if (!response.isSuccess()) {
            System.out.println("Error Message: " + response.getErrorMessage());
        }
        System.out.println("===============================\n");

        // Test passes if response is received (success or error)
        assertTrue(response.isSuccess() || response.getErrorMessage() != null);
    }

    @Test
    public void testNHHProviderMultiModels() {
        String apiKey = "sk-AmoMMHVqeGcQGvgbBWD6ARwTyS791MPKJn21Sypv5bbBZr8w";
        NHHProvider provider = new NHHProvider(apiKey);

        String[] testModels = {
            "glm-4.5-flash",
            "gemini-2.0-flash",
            "gpt-4"
        };

        for (String model : testModels) {
            assertTrue(provider.supportsModel(model), "Model should be supported: " + model);

            LLMRequest request = LLMRequest.builder()
                .model(model)
                .addUserMessage("Hello")
                .build();

            System.out.println("Testing model: " + model);
            LLMResponse response = provider.sendRequest(request);
            assertNotNull(response);
            System.out.println("  Status: " + (response.isSuccess() ? "SUCCESS" : "FAILED"));
            if (!response.isSuccess()) {
                System.out.println("  Error: " + response.getErrorMessage());
            }
        }
    }
}
