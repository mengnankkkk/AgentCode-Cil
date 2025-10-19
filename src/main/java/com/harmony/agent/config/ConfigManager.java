package com.harmony.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration manager for HarmonySafeAgent
 * Manages loading, saving, and accessing configuration
 */
public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static ConfigManager instance;

    private static final String CONFIG_DIR_NAME = ".harmony-agent";
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String SECURE_CONFIG_FILE = "secure.dat";

    private final Path configDir;
    private final Path configFile;
    private final Path secureConfigFile;

    private AppConfig config;
    private final SecureConfigManager secureManager;

    private ConfigManager() {
        String userHome = System.getProperty("user.home");
        this.configDir = Paths.get(userHome, CONFIG_DIR_NAME);
        this.configFile = configDir.resolve(CONFIG_FILE_NAME);
        this.secureConfigFile = configDir.resolve(SECURE_CONFIG_FILE);
        this.secureManager = new SecureConfigManager(secureConfigFile);

        initializeConfigDir();
        loadConfiguration();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Initialize configuration directory
     */
    private void initializeConfigDir() {
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                logger.info("Created configuration directory: {}", configDir);
            }

            // Create logs directory
            Path logsDir = configDir.resolve("logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }

            // Create cache directory
            Path cacheDir = configDir.resolve("cache");
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }

        } catch (IOException e) {
            logger.error("Failed to create configuration directory", e);
        }
    }

    /**
     * Load configuration from file
     */
    private void loadConfiguration() {
        config = new AppConfig();

        InputStream input = null;
        String configSource = null;

        try {
            // Priority 1: Load from classpath (application.yml in resources) - PROJECT CONFIG
            input = getClass().getClassLoader().getResourceAsStream("application.yml");
            if (input != null) {
                configSource = "classpath:application.yml";
                logger.info("Loading configuration from: {}", configSource);
            }
            // Priority 2: Load from user config file (~/.harmony-agent/config.yml) - USER OVERRIDE
            else if (Files.exists(configFile)) {
                input = Files.newInputStream(configFile);
                configSource = configFile.toString();
                logger.info("Loading configuration from: {}", configSource);
            }
            else {
                logger.warn("No configuration file found, using defaults");
                return;
            }

            // Load from YAML
            Yaml yaml = new Yaml();
            Map<String, Map<String, Object>> loadedConfig = yaml.load(input);

            if (loadedConfig != null) {
                // Load AI config
                Map<String, Object> aiMap = loadedConfig.get("ai");
                if (aiMap != null) {
                    if (aiMap.containsKey("provider")) config.getAi().setProvider((String) aiMap.get("provider"));
                    if (aiMap.containsKey("model")) config.getAi().setModel((String) aiMap.get("model"));
                    if (aiMap.containsKey("max_tokens")) config.getAi().setMaxTokens(((Number) aiMap.get("max_tokens")).intValue());
                    if (aiMap.containsKey("temperature")) config.getAi().setTemperature(((Number) aiMap.get("temperature")).doubleValue());
                    if (aiMap.containsKey("base_url")) config.getAi().setBaseUrl((String) aiMap.get("base_url"));

                    // Load rate limiting configuration
                    if (aiMap.containsKey("rate_limit_mode")) config.getAi().setRateLimitMode((String) aiMap.get("rate_limit_mode"));
                    if (aiMap.containsKey("requests_per_second_limit")) config.getAi().setRequestsPerSecondLimit(((Number) aiMap.get("requests_per_second_limit")).doubleValue());
                    if (aiMap.containsKey("tokens_per_minute_limit")) config.getAi().setTokensPerMinuteLimit(((Number) aiMap.get("tokens_per_minute_limit")).intValue());
                    if (aiMap.containsKey("safety_margin")) config.getAi().setSafetyMargin(((Number) aiMap.get("safety_margin")).doubleValue());
                    if (aiMap.containsKey("validation_concurrency")) config.getAi().setValidationConcurrency(((Number) aiMap.get("validation_concurrency")).intValue());

                    // Load providers configuration
                    if (aiMap.containsKey("providers")) {
                            Map<String, Map<String, Object>> providersMap = (Map<String, Map<String, Object>>) aiMap.get("providers");
                            for (Map.Entry<String, Map<String, Object>> entry : providersMap.entrySet()) {
                                String providerName = entry.getKey();
                                Map<String, Object> providerData = entry.getValue();

                                AppConfig.ProviderConfig providerConfig = new AppConfig.ProviderConfig();
                                if (providerData.containsKey("api_key")) {
                                    providerConfig.setApiKey((String) providerData.get("api_key"));
                                }
                                if (providerData.containsKey("base_url")) {
                                    providerConfig.setBaseUrl((String) providerData.get("base_url"));
                                }
                                if (providerData.containsKey("models")) {
                                    providerConfig.setModels((Map<String, String>) providerData.get("models"));
                                }

                                config.getAi().getProviders().put(providerName, providerConfig);
                            }
                        }

                    // Load roles configuration
                    if (aiMap.containsKey("roles")) {
                            Map<String, Map<String, Object>> rolesMap = (Map<String, Map<String, Object>>) aiMap.get("roles");
                            for (Map.Entry<String, Map<String, Object>> entry : rolesMap.entrySet()) {
                                String roleName = entry.getKey();
                                Map<String, Object> roleData = entry.getValue();

                                AppConfig.RoleConfig roleConfig = new AppConfig.RoleConfig();
                                if (roleData.containsKey("provider")) {
                                    roleConfig.setProvider((String) roleData.get("provider"));
                                }
                                if (roleData.containsKey("model")) {
                                    roleConfig.setModel((String) roleData.get("model"));
                                }
                                if (roleData.containsKey("temperature")) {
                                    roleConfig.setTemperature(((Number) roleData.get("temperature")).doubleValue());
                                }
                                if (roleData.containsKey("max_tokens")) {
                                    roleConfig.setMaxTokens(((Number) roleData.get("max_tokens")).intValue());
                                }

                                config.getAi().getRoles().put(roleName, roleConfig);
                            }
                        }

                    // Load commands configuration
                    if (aiMap.containsKey("commands")) {
                            Map<String, Map<String, Object>> commandsMap = (Map<String, Map<String, Object>>) aiMap.get("commands");
                            for (Map.Entry<String, Map<String, Object>> entry : commandsMap.entrySet()) {
                                String commandName = entry.getKey();
                                Map<String, Object> commandData = entry.getValue();

                                AppConfig.CommandConfig commandConfig = new AppConfig.CommandConfig();
                                if (commandData.containsKey("provider")) {
                                    commandConfig.setProvider((String) commandData.get("provider"));
                                }
                                if (commandData.containsKey("model")) {
                                    commandConfig.setModel((String) commandData.get("model"));
                                }
                                if (commandData.containsKey("temperature")) {
                                    commandConfig.setTemperature(((Number) commandData.get("temperature")).doubleValue());
                                }
                                if (commandData.containsKey("max_tokens")) {
                                    commandConfig.setMaxTokens(((Number) commandData.get("max_tokens")).intValue());
                                }

                                config.getAi().getCommands().put(commandName, commandConfig);
                            }
                        }
                    }

                    // Load Analysis config
                    Map<String, Object> analysisMap = loadedConfig.get("analysis");
                    if (analysisMap != null) {
                        if (analysisMap.containsKey("level")) config.getAnalysis().setLevel((String) analysisMap.get("level"));
                        if (analysisMap.containsKey("parallel")) config.getAnalysis().setParallel((Boolean) analysisMap.get("parallel"));
                        if (analysisMap.containsKey("max_threads")) config.getAnalysis().setMaxThreads(((Number) analysisMap.get("max_threads")).intValue());
                        if (analysisMap.containsKey("incremental")) config.getAnalysis().setIncremental((Boolean) analysisMap.get("incremental"));
                        if (analysisMap.containsKey("timeout")) config.getAnalysis().setTimeout(((Number) analysisMap.get("timeout")).intValue());
                    }

                    // Load Tools config
                    Map<String, Object> toolsMap = loadedConfig.get("tools");
                    if (toolsMap != null) {
                        if (toolsMap.containsKey("clang_path")) config.getTools().setClangPath((String) toolsMap.get("clang_path"));
                        if (toolsMap.containsKey("semgrep_path")) config.getTools().setSemgrepPath((String) toolsMap.get("semgrep_path"));
                        if (toolsMap.containsKey("rust_path")) config.getTools().setRustPath((String) toolsMap.get("rust_path"));
                    }

                    // Load Output config
                    Map<String, Object> outputMap = loadedConfig.get("output");
                    if (outputMap != null) {
                        if (outputMap.containsKey("format")) config.getOutput().setFormat((String) outputMap.get("format"));
                        if (outputMap.containsKey("verbose")) config.getOutput().setVerbose((Boolean) outputMap.get("verbose"));
                        if (outputMap.containsKey("color")) config.getOutput().setColor((Boolean) outputMap.get("color"));
                    }

                    // Load Cache config
                    Map<String, Object> cacheMap = loadedConfig.get("cache");
                    if (cacheMap != null) {
                        if (cacheMap.containsKey("enabled")) config.getCache().setEnabled((Boolean) cacheMap.get("enabled"));
                        if (cacheMap.containsKey("ttl")) config.getCache().setTtl(((Number) cacheMap.get("ttl")).intValue());
                        if (cacheMap.containsKey("max_size")) config.getCache().setMaxSize(((Number) cacheMap.get("max_size")).intValue());
                    }

                logger.info("Configuration loaded successfully from: {}", configSource);
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration from: {}", configSource, e);
        } catch (Exception e) {
            logger.error("Failed to initialize configuration", e);
        } finally {
            // Close input stream
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        // Load API key from secure storage
        try {
            String apiKey = secureManager.loadApiKey();
            if (apiKey != null && !apiKey.isEmpty()) {
                config.getAi().setApiKey(apiKey);
            }
        } catch (Exception e) {
            logger.debug("No secure API key found");
        }

        // Override with environment variables
        overrideWithEnvVars();
    }

    /**
     * Override configuration with environment variables
     */
    private void overrideWithEnvVars() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey != null && !apiKey.isEmpty()) {
            config.getAi().setApiKey(apiKey);
            logger.info("Using API key from environment variable");
        }

        String provider = System.getenv("HARMONY_AI_PROVIDER");
        if (provider != null) {
            config.getAi().setProvider(provider);
        }

        String model = System.getenv("HARMONY_AI_MODEL");
        if (model != null) {
            config.getAi().setModel(model);
        }
    }

    /**
     * Save configuration to file
     */
    public void saveConfiguration() {
        try (Writer writer = new FileWriter(configFile.toFile())) {
            // Use custom representer to avoid Java type tags
            org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
            options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);

            Yaml yaml = new Yaml(options);

            // Convert to map for safe YAML serialization
            Map<String, Object> configMap = new HashMap<>();

            Map<String, Object> aiMap = new HashMap<>();
            aiMap.put("provider", config.getAi().getProvider());
            aiMap.put("model", config.getAi().getModel());
            aiMap.put("max_tokens", config.getAi().getMaxTokens());
            aiMap.put("temperature", config.getAi().getTemperature());
            aiMap.put("base_url", config.getAi().getBaseUrl());
            configMap.put("ai", aiMap);

            Map<String, Object> analysisMap = new HashMap<>();
            analysisMap.put("level", config.getAnalysis().getLevel());
            analysisMap.put("parallel", config.getAnalysis().isParallel());
            analysisMap.put("max_threads", config.getAnalysis().getMaxThreads());
            analysisMap.put("incremental", config.getAnalysis().isIncremental());
            analysisMap.put("timeout", config.getAnalysis().getTimeout());
            configMap.put("analysis", analysisMap);

            Map<String, Object> toolsMap = new HashMap<>();
            toolsMap.put("clang_path", config.getTools().getClangPath());
            toolsMap.put("semgrep_path", config.getTools().getSemgrepPath());
            toolsMap.put("rust_path", config.getTools().getRustPath());
            configMap.put("tools", toolsMap);

            Map<String, Object> outputMap = new HashMap<>();
            outputMap.put("format", config.getOutput().getFormat());
            outputMap.put("verbose", config.getOutput().isVerbose());
            outputMap.put("color", config.getOutput().isColor());
            configMap.put("output", outputMap);

            Map<String, Object> cacheMap = new HashMap<>();
            cacheMap.put("enabled", config.getCache().isEnabled());
            cacheMap.put("ttl", config.getCache().getTtl());
            cacheMap.put("max_size", config.getCache().getMaxSize());
            configMap.put("cache", cacheMap);

            yaml.dump(configMap, writer);
            logger.info("Configuration saved to: {}", configFile);
        } catch (IOException e) {
            logger.error("Failed to save configuration", e);
        }
    }

    /**
     * Get configuration value by key
     */
    public String get(String key) {
        Map<String, String> flatMap = config.toFlatMap();
        String value = flatMap.get(key);

        // Special handling for API key (show masked)
        if ("ai.api_key".equals(key) && value != null) {
            return maskApiKey(value);
        }

        return value;
    }

    /**
     * Set configuration value by key
     */
    public void set(String key, String value) {
        try {
            // Special handling for API key
            if ("ai.api_key".equals(key)) {
                config.getAi().setApiKey(value);
                secureManager.saveApiKey(value);
                logger.info("API key saved securely");
                return;
            }

            // Parse key and set value
            String[] parts = key.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid key format: " + key);
            }

            String section = parts[0];
            String field = parts[1];

            switch (section) {
                case "ai" -> setAiConfig(field, value);
                case "analysis" -> setAnalysisConfig(field, value);
                case "tools" -> setToolsConfig(field, value);
                case "output" -> setOutputConfig(field, value);
                case "cache" -> setCacheConfig(field, value);
                default -> throw new IllegalArgumentException("Unknown section: " + section);
            }

            saveConfiguration();
            logger.info("Configuration updated: {} = {}", key, value);

        } catch (Exception e) {
            logger.error("Failed to set configuration", e);
            throw new RuntimeException("Failed to set configuration: " + e.getMessage());
        }
    }

    private void setAiConfig(String field, String value) {
        switch (field) {
            case "provider" -> config.getAi().setProvider(value);
            case "model" -> config.getAi().setModel(value);
            case "max_tokens" -> config.getAi().setMaxTokens(Integer.parseInt(value));
            case "temperature" -> config.getAi().setTemperature(Double.parseDouble(value));
            case "base_url" -> config.getAi().setBaseUrl(value);
            default -> throw new IllegalArgumentException("Unknown AI config field: " + field);
        }
    }

    private void setAnalysisConfig(String field, String value) {
        switch (field) {
            case "level" -> config.getAnalysis().setLevel(value);
            case "parallel" -> config.getAnalysis().setParallel(Boolean.parseBoolean(value));
            case "max_threads" -> config.getAnalysis().setMaxThreads(Integer.parseInt(value));
            case "incremental" -> config.getAnalysis().setIncremental(Boolean.parseBoolean(value));
            case "timeout" -> config.getAnalysis().setTimeout(Integer.parseInt(value));
            default -> throw new IllegalArgumentException("Unknown analysis config field: " + field);
        }
    }

    private void setToolsConfig(String field, String value) {
        switch (field) {
            case "clang_path" -> config.getTools().setClangPath(value);
            case "semgrep_path" -> config.getTools().setSemgrepPath(value);
            case "rust_path" -> config.getTools().setRustPath(value);
            default -> throw new IllegalArgumentException("Unknown tools config field: " + field);
        }
    }

    private void setOutputConfig(String field, String value) {
        switch (field) {
            case "format" -> config.getOutput().setFormat(value);
            case "verbose" -> config.getOutput().setVerbose(Boolean.parseBoolean(value));
            case "color" -> config.getOutput().setColor(Boolean.parseBoolean(value));
            default -> throw new IllegalArgumentException("Unknown output config field: " + field);
        }
    }

    private void setCacheConfig(String field, String value) {
        switch (field) {
            case "enabled" -> config.getCache().setEnabled(Boolean.parseBoolean(value));
            case "ttl" -> config.getCache().setTtl(Integer.parseInt(value));
            case "max_size" -> config.getCache().setMaxSize(Integer.parseInt(value));
            default -> throw new IllegalArgumentException("Unknown cache config field: " + field);
        }
    }

    /**
     * List all configuration values
     */
    public Map<String, String> list() {
        return config.toFlatMap();
    }

    /**
     * Get configuration object
     */
    public AppConfig getConfig() {
        return config;
    }

    /**
     * Get configuration directory path
     */
    public Path getConfigDir() {
        return configDir;
    }

    /**
     * Mask API key for display
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * Validate configuration
     */
    public boolean validate() {
        try {
            // Check if API key is set
            String apiKey = config.getAi().getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                logger.warn("API key not configured");
                return false;
            }

            // Check if analysis level is valid
            String level = config.getAnalysis().getLevel();
            if (!level.matches("quick|standard|deep")) {
                logger.error("Invalid analysis level: {}", level);
                return false;
            }

            // Check if output format is valid
            String format = config.getOutput().getFormat();
            if (!format.matches("html|markdown|json")) {
                logger.error("Invalid output format: {}", format);
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("Configuration validation failed", e);
            return false;
        }
    }
}
