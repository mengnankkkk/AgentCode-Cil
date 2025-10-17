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

    public static class AiConfig {
        private String provider = "openai";
        private String apiKey;
        private String model = "gpt-4-turbo";
        private int maxTokens = 4096;
        private double temperature = 0.3;
        private String baseUrl = "https://api.openai.com/v1";

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
    }

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

    public static class ToolsConfig {
        private String clangPath = "clang";
        private String semgrepPath = "semgrep";
        private String rustPath = "rustc";

        // Getters and setters
        public String getClangPath() { return clangPath; }
        public void setClangPath(String clangPath) { this.clangPath = clangPath; }

        public String getSemgrepPath() { return semgrepPath; }
        public void setSemgrepPath(String semgrepPath) { this.semgrepPath = semgrepPath; }

        public String getRustPath() { return rustPath; }
        public void setRustPath(String rustPath) { this.rustPath = rustPath; }
    }

    public static class OutputConfig {
        private String format = "html";
        private boolean verbose = true;
        private boolean color = true;

        // Getters and setters
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public boolean isVerbose() { return verbose; }
        public void setVerbose(boolean verbose) { this.verbose = verbose; }

        public boolean isColor() { return color; }
        public void setColor(boolean color) { this.color = color; }
    }

    public static class CacheConfig {
        private boolean enabled = true;
        private int ttl = 3600;
        private int maxSize = 100;

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getTtl() { return ttl; }
        public void setTtl(int ttl) { this.ttl = ttl; }

        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
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

        map.put("output.format", output.format);
        map.put("output.verbose", String.valueOf(output.verbose));
        map.put("output.color", String.valueOf(output.color));

        map.put("cache.enabled", String.valueOf(cache.enabled));
        map.put("cache.ttl", String.valueOf(cache.ttl));
        map.put("cache.max_size", String.valueOf(cache.maxSize));

        return map;
    }
}
