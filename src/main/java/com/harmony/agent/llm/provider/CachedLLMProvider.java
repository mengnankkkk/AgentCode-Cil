package com.harmony.agent.llm.provider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.harmony.agent.core.ai.PersistentCacheManager;
import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CachedLLMProvider - LLMProvider 的缓存装饰器
 *
 * 使用 PersistentCacheManager 的两层缓存架构：
 * - L1 缓存（内存）：Guava Cache，<1ms 访问
 * - L2 缓存（磁盘）：文件系统，~5ms 访问
 *
 * 缓存键生成策略：
 * - 基于 model、temperature、system messages、user messages 生成 SHA-256 哈希
 * - 确保相同输入返回相同的缓存键
 * - 不考虑 maxTokens（不影响 LLM 的核心输出）
 *
 * 线程安全：继承 PersistentCacheManager 的线程安全机制
 */
public class CachedLLMProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(CachedLLMProvider.class);

    private final LLMProvider delegate;
    private final PersistentCacheManager cache;
    private final Gson gson;

    /**
     * 构造函数
     *
     * @param delegate 实际的 LLM provider（OpenAI、Claude 等）
     * @param cacheType 缓存类型标识（用于 PersistentCacheManager）
     */
    public CachedLLMProvider(LLMProvider delegate, String cacheType) {
        this.delegate = delegate;
        this.cache = new PersistentCacheManager(cacheType, true);
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        logger.info("CachedLLMProvider initialized for {}, cache type: {}",
            delegate.getProviderName(), cacheType);
    }

    /**
     * 构造函数（使用默认缓存类型）
     *
     * @param delegate 实际的 LLM provider
     */
    public CachedLLMProvider(LLMProvider delegate) {
        this(delegate, "ai_llm_calls");
    }

    @Override
    public LLMResponse sendRequest(LLMRequest request) {
        // 1. 生成缓存键
        String cacheKey = generateCacheKey(request);
        String keyPrefix = cacheKey.substring(0, Math.min(16, cacheKey.length()));

        // 2. L1 + L2 查找
        String cachedResponse = cache.get(cacheKey);
        if (cachedResponse != null) {
            logger.info("Cache HIT for key: {}..., provider: {}",
                keyPrefix, delegate.getProviderName());
            try {
                return deserializeLLMResponse(cachedResponse);
            } catch (Exception e) {
                logger.warn("Failed to deserialize cached response, will call LLM: {}", e.getMessage());
                // 缓存损坏，继续调用 LLM
            }
        }

        // 3. Cache MISS - 调用实际的 provider
        logger.info("Cache MISS for key: {}..., provider: {}, calling LLM",
            keyPrefix, delegate.getProviderName());

        long startTime = System.currentTimeMillis();
        LLMResponse response = delegate.sendRequest(request);
        long duration = System.currentTimeMillis() - startTime;

        // 4. 缓存结果（如果成功）
        if (response.isSuccess()) {
            try {
                String serialized = serializeLLMResponse(response);
                cache.put(cacheKey, serialized);
                logger.info("Cached response for key: {}..., size: {} bytes, LLM call took {}ms",
                    keyPrefix, serialized.length(), duration);
            } catch (Exception e) {
                logger.warn("Failed to cache response: {}", e.getMessage());
                // 缓存失败不影响主流程
            }
        } else {
            logger.warn("LLM call failed, not caching: {}", response.getErrorMessage());
        }

        return response;
    }

    /**
     * 生成缓存键
     *
     * 策略：基于关键参数生成 SHA-256 哈希
     * - model：不同模型结果不同
     * - temperature：影响输出随机性
     * - system messages：定义角色和规则
     * - user messages：实际的输入内容
     *
     * 不考虑的因素：
     * - maxTokens：不影响 LLM 的核心输出逻辑
     * - stream：与结果无关
     */
    private String generateCacheKey(LLMRequest request) {
        StringBuilder keyContent = new StringBuilder();

        // Model
        keyContent.append("model=").append(request.getModel()).append("|");

        // Temperature (保留2位小数)
        keyContent.append("temp=").append(String.format("%.2f", request.getTemperature())).append("|");

        // System messages
        List<Message> systemMessages = request.getMessages().stream()
            .filter(msg -> msg.getRole() == Message.MessageRole.SYSTEM)
            .toList();
        keyContent.append("system=").append(hashMessages(systemMessages)).append("|");

        // User messages
        List<Message> userMessages = request.getMessages().stream()
            .filter(msg -> msg.getRole() == Message.MessageRole.USER)
            .toList();
        keyContent.append("user=").append(hashMessages(userMessages));

        // SHA-256 哈希
        return hashSHA256(keyContent.toString());
    }

    /**
     * 对消息列表进行哈希
     */
    private String hashMessages(List<Message> messages) {
        if (messages.isEmpty()) {
            return "empty";
        }

        String concatenated = messages.stream()
            .map(Message::getContent)
            .collect(Collectors.joining("|"));

        return hashSHA256(concatenated);
    }

    /**
     * SHA-256 哈希
     */
    private String hashSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 应该始终可用
            logger.error("SHA-256 not available, falling back to simple hash", e);
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 序列化 LLMResponse 为 JSON
     */
    private String serializeLLMResponse(LLMResponse response) {
        return gson.toJson(response);
    }

    /**
     * 从 JSON 反序列化 LLMResponse
     */
    private LLMResponse deserializeLLMResponse(String json) {
        return gson.fromJson(json, LLMResponse.class);
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计
     */
    public PersistentCacheManager.CacheStats getCacheStats() {
        return cache.getStats();
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        cache.clear();
        logger.info("Cache cleared for provider: {}", delegate.getProviderName());
    }

    // ========================================
    // LLMProvider 接口实现（委托给 delegate）
    // ========================================

    @Override
    public String getProviderName() {
        return delegate.getProviderName() + " (cached)";
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public String[] getAvailableModels() {
        return delegate.getAvailableModels();
    }

    @Override
    public boolean supportsModel(String model) {
        return delegate.supportsModel(model);
    }

    /**
     * 获取底层的 delegate provider（用于调试）
     *
     * @return 实际的 LLM provider
     */
    public LLMProvider getDelegate() {
        return delegate;
    }
}
