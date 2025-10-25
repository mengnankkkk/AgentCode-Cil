package com.harmony.agent.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Application configuration model
 */
public class AppConfig {

    private AiConfig ai = new AiConfig();
    private AnalysisConfig analysis = new AnalysisConfig();
    private ToolsConfig tools = new ToolsConfig();
    private OutputConfig output = new OutputConfig();
    private CacheConfig cache = new CacheConfig();

    /**
     * AI configuration
     */
    public static class AiConfig {
        private String provider = "openai";
        private String apiKey;
        private String model = "gpt-4-turbo";
        private int maxTokens = 4096;
        private double temperature = 0.3;
        private String baseUrl = "https://api.openai.com/v1";

        // Rate limiting configuration
        private String rateLimitMode = "qps"; // "qps" (queries per second) or "tpm" (tokens per minute)
        private double requestsPerSecondLimit = 5.0; // QPS mode: max requests per second
        private int tokensPerMinuteLimit = 60000; // TPM mode: max tokens per minute
        private double safetyMargin = 0.8; // Safety margin (0.8 = 80% of limit)
        private int validationConcurrency = 3; // Default: max 3 concurrent validations

        // Multiple providers configuration
        private Map<String, ProviderConfig> providers = new HashMap<>();

        // Role-based model selection
        private Map<String, RoleConfig> roles = new HashMap<>();

        // Command-based model selection
        private Map<String, CommandConfig> commands = new HashMap<>();

        // Getters and setters
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getRateLimitMode() { return rateLimitMode; }
        public void setRateLimitMode(String rateLimitMode) { this.rateLimitMode = rateLimitMode; }

        public double getRequestsPerSecondLimit() { return requestsPerSecondLimit; }
        public void setRequestsPerSecondLimit(double requestsPerSecondLimit) {
            this.requestsPerSecondLimit = requestsPerSecondLimit;
        }

        public int getTokensPerMinuteLimit() { return tokensPerMinuteLimit; }
        public void setTokensPerMinuteLimit(int tokensPerMinuteLimit) {
            this.tokensPerMinuteLimit = tokensPerMinuteLimit;
        }

        public double getSafetyMargin() { return safetyMargin; }
        public void setSafetyMargin(double safetyMargin) { this.safetyMargin = safetyMargin; }

        public int getValidationConcurrency() { return validationConcurrency; }
        public void setValidationConcurrency(int validationConcurrency) {
            this.validationConcurrency = validationConcurrency;
        }

        public Map<String, ProviderConfig> getProviders() { return providers; }
        public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }

        public Map<String, RoleConfig> getRoles() { return roles; }
        public void setRoles(Map<String, RoleConfig> roles) { this.roles = roles; }

        public Map<String, CommandConfig> getCommands() { return commands; }
        public void setCommands(Map<String, CommandConfig> commands) { this.commands = commands; }
    }

    /**
     * Provider configuration
     */
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private Map<String, String> models = new HashMap<>();

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public Map<String, String> getModels() { return models; }
        public void setModels(Map<String, String> models) { this.models = models; }
    }

    /**
     * Role configuration
     */
    public static class RoleConfig {
        private String provider;
        private String model;
        private double temperature = 0.5;
        private int maxTokens = 2000;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    /**
     * Command configuration (for CLI commands)
     */
    public static class CommandConfig {
        private String provider;
        private String model;
        private double temperature = 0.3;
        private int maxTokens = 4096;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    /**
     * Analysis configuration
     */
    public static class AnalysisConfig {
        private String level = "standard";
        private boolean parallel = true;
        private int maxThreads = 4;
        private boolean incremental = false;
        private int timeout = 300;

        // Getters and setters
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }

        public boolean isParallel() { return parallel; }
        public void setParallel(boolean parallel) { this.parallel = parallel; }

        public int getMaxThreads() { return maxThreads; }
        public void setMaxThreads(int maxThreads) { this.maxThreads = maxThreads; }

        public boolean isIncremental() { return incremental; }
        public void setIncremental(boolean incremental) { this.incremental = incremental; }

        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
    }

    /**
     * Tools configuration
     */
    public static class ToolsConfig {
        private String clangPath = "clang";
        private String semgrepPath = "semgrep";
        private String rustPath = "rustc";
        private String cargoPath = "cargo";  // Rust build tool

        // Getters and setters
        public String getClangPath() { return clangPath; }
        public void setClangPath(String clangPath) { this.clangPath = clangPath; }

        public String getSemgrepPath() { return semgrepPath; }
        public void setSemgrepPath(String semgrepPath) { this.semgrepPath = semgrepPath; }

        public String getRustPath() { return rustPath; }
        public void setRustPath(String rustPath) { this.rustPath = rustPath; }

        public String getCargoPath() { return cargoPath; }
        public void setCargoPath(String cargoPath) { this.cargoPath = cargoPath; }
    }

    /**
     * Output configuration
     */
    public static class OutputConfig {
        private String format = "html";
        private boolean verbose = true;
        private boolean color = true;
        private int commandHistorySize = 10;  // Number of commands to keep in history

        // Getters and setters
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public boolean isVerbose() { return verbose; }
        public void setVerbose(boolean verbose) { this.verbose = verbose; }

        public boolean isColor() { return color; }
        public void setColor(boolean color) { this.color = color; }

        public int getCommandHistorySize() { return commandHistorySize; }
        public void setCommandHistorySize(int commandHistorySize) {
            this.commandHistorySize = commandHistorySize;
        }
    }

    /**
     * Cache configuration
     */
    public static class CacheConfig {
        private boolean enabled = true;
        private int ttl = 3600;  // L1 cache TTL in seconds (default: 1 hour)
        private int maxSize = 100;
        private String type = "ai_llm_calls";  // Cache type identifier for PersistentCacheManager
        private int l2TtlDays = 7;  // L2 cache TTL in days (default: 7 days)

        // LLM cache specific
        private boolean llmCacheEnabled = true;  // Enable LLM provider cache
        private String llmCacheType = "ai_llm_calls";  // Cache type for LLM calls

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getTtl() { return ttl; }
        public void setTtl(int ttl) { this.ttl = ttl; }

        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public int getL2TtlDays() { return l2TtlDays; }
        public void setL2TtlDays(int l2TtlDays) { this.l2TtlDays = l2TtlDays; }

        public boolean isLlmCacheEnabled() { return llmCacheEnabled; }
        public void setLlmCacheEnabled(boolean llmCacheEnabled) { this.llmCacheEnabled = llmCacheEnabled; }

        public String getLlmCacheType() { return llmCacheType; }
        public void setLlmCacheType(String llmCacheType) { this.llmCacheType = llmCacheType; }
    }

    // Main config getters and setters
    public AiConfig getAi() { return ai; }
    public void setAi(AiConfig ai) { this.ai = ai; }

    public AnalysisConfig getAnalysis() { return analysis; }
    public void setAnalysis(AnalysisConfig analysis) { this.analysis = analysis; }

    public ToolsConfig getTools() { return tools; }
    public void setTools(ToolsConfig tools) { this.tools = tools; }

    public OutputConfig getOutput() { return output; }
    public void setOutput(OutputConfig output) { this.output = output; }

    public CacheConfig getCache() { return cache; }
    public void setCache(CacheConfig cache) { this.cache = cache; }

    /**
     * Convert config to flat map for easy access
     */
    public Map<String, String> toFlatMap() {
        Map<String, String> map = new HashMap<>();

        map.put("ai.provider", ai.provider);
        map.put("ai.model", ai.model);
        map.put("ai.max_tokens", String.valueOf(ai.maxTokens));
        map.put("ai.temperature", String.valueOf(ai.temperature));
        map.put("ai.base_url", ai.baseUrl);

        map.put("analysis.level", analysis.level);
        map.put("analysis.parallel", String.valueOf(analysis.parallel));
        map.put("analysis.max_threads", String.valueOf(analysis.maxThreads));
        map.put("analysis.incremental", String.valueOf(analysis.incremental));
        map.put("analysis.timeout", String.valueOf(analysis.timeout));

        map.put("tools.clang_path", tools.clangPath);
        map.put("tools.semgrep_path", tools.semgrepPath);
        map.put("tools.rust_path", tools.rustPath);
        map.put("tools.cargo_path", tools.cargoPath);

        map.put("output.format", output.format);
        map.put("output.verbose", String.valueOf(output.verbose));
        map.put("output.color", String.valueOf(output.color));

        map.put("cache.enabled", String.valueOf(cache.enabled));
        map.put("cache.ttl", String.valueOf(cache.ttl));
        map.put("cache.max_size", String.valueOf(cache.maxSize));

        return map;
    }
}
