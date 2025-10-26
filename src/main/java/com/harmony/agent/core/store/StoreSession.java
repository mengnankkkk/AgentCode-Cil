package com.harmony.agent.core.store;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一问题存储会话管理器
 *
 * 职责：
 * - 管理 UnifiedIssueStore 的生命周期
 * - 提供会话隔离（每个交互会话一个独立的 Store）
 * - 支持会话持久化和恢复
 *
 * 使用场景：
 * - 在 InteractiveCommand 启动时创建会话
 * - 在所有交互命令之间共享同一个 Store 实例
 * - 在退出时可选持久化到 ~/.harmony-agent/session-cache.json
 * - 下次启动时可选恢复上次的分析结果
 */
public class StoreSession {

    private final String sessionId;
    private final LocalDateTime createdAt;
    private final UnifiedIssueStore store;
    private LocalDateTime lastModified;

    /**
     * 创建新会话
     */
    public StoreSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
        this.store = new UnifiedIssueStore();
    }

    /**
     * 从磁盘恢复会话
     *
     * @param sessionPath 会话文件路径
     */
    public StoreSession(Path sessionPath) throws Exception {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
        this.store = UnifiedIssueStore.loadFromDisk(sessionPath);
    }

    /**
     * 获取会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取最后修改时间
     */
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    /**
     * 获取 UnifiedIssueStore 实例
     *
     * @return 当前会话的 Store 实例
     */
    public UnifiedIssueStore getStore() {
        return store;
    }

    /**
     * 更新最后修改时间
     *
     * 在 Store 被修改时调用
     */
    public void touch() {
        this.lastModified = LocalDateTime.now();
    }

    /**
     * 保存会话到磁盘
     *
     * 默认路径：~/.harmony-agent/session-cache.json
     *
     * @throws Exception 如果保存失败
     */
    public void save() throws Exception {
        Path sessionCachePath = getDefaultSessionCachePath();
        store.saveToDisk(sessionCachePath);
    }

    /**
     * 获取默认的会话缓存路径
     *
     * @return ~/.harmony-agent/session-cache.json
     */
    public static Path getDefaultSessionCachePath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".harmony-agent", "session-cache.json");
    }

    /**
     * 清空会话数据
     */
    public void clear() {
        store.clear();
        touch();
    }

    /**
     * 获取会话统计信息
     */
    public String getStatistics() {
        return String.format(
            "Session[id=%s, created=%s, modified=%s, %s]",
            sessionId.substring(0, 8),
            createdAt,
            lastModified,
            store.getStatistics()
        );
    }

    @Override
    public String toString() {
        return getStatistics();
    }
}
