package com.harmony.agent.core.store;

import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.model.ScanResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一问题存储中心 - 中央问题库
 *
 * 职责：
 * - 作为所有问题发现命令（analyze, review）的数据汇聚点
 * - 提供统一的去重和合并逻辑
 * - 支持多维度查询
 * - 为问题处理命令（autofix, refactor）提供上下文
 *
 * 设计模式：Single Source of Truth
 * - 生产者：AnalysisEngine, ReviewCommand
 * - 消费者：ReportCommand, AutoFixOrchestrator, RefactorCommand
 */
public class UnifiedIssueStore {

    // 核心存储：使用问题哈希值作为唯一键
    private final Map<String, SecurityIssue> issues = new ConcurrentHashMap<>();

    // 二级索引：文件路径 -> 问题哈希列表（优化查询性能）
    private final Map<String, List<String>> fileIndex = new ConcurrentHashMap<>();

    /**
     * 添加单个问题（含去重逻辑）
     *
     * 合并规则：
     * 1. 如果问题不存在（同一位置、同一类别）：直接添加
     * 2. 如果问题已存在：执行合并
     *    - AI审查（review）的信息优先级高于SAST（analyze）
     *    - 选择"更丰富"的版本（修复建议、影响分析等）
     *    - 记录来源以供审计
     *
     * @param issue 要添加的问题
     */
    public synchronized void addIssue(SecurityIssue issue) {
        if (issue == null) {
            return;
        }

        String uid = issue.getHash();

        if (issues.containsKey(uid)) {
            // 问题已存在 - 执行合并
            SecurityIssue existing = issues.get(uid);
            SecurityIssue merged = mergeIssues(existing, issue);
            issues.put(uid, merged);
        } else {
            // 问题不存在 - 直接添加
            issues.put(uid, issue);

            // 更新文件索引
            updateFileIndex(issue);
        }
    }

    /**
     * 批量添加问题
     *
     * @param newIssues 问题集合
     */
    public void addIssues(Collection<SecurityIssue> newIssues) {
        if (newIssues == null || newIssues.isEmpty()) {
            return;
        }

        for (SecurityIssue issue : newIssues) {
            addIssue(issue);
        }
    }

    /**
     * 合并两个问题对象
     *
     * 策略：选择"更丰富"的版本
     * - 如果新问题来自AI审查（修复建议字段非空），优先使用新问题
     * - 否则保持现有问题
     * - 记录合并来源以供审计
     *
     * @param existing 现有问题
     * @param newIssue 新问题
     * @return 合并后的问题
     */
    private SecurityIssue mergeIssues(SecurityIssue existing, SecurityIssue newIssue) {
        // 如果新问题有修复建议（来自AI），则优先选择新问题
        Object newFixSuggestion = newIssue.getMetadata().get("fix_suggestion");
        Object existingFixSuggestion = existing.getMetadata().get("fix_suggestion");

        SecurityIssue richer = (newFixSuggestion != null && existingFixSuggestion == null)
            ? newIssue
            : existing;

        // 记录合并信息
        String analyzer1 = existing.getAnalyzer();
        String analyzer2 = newIssue.getAnalyzer();

        // 更新或添加 merged_from 标签
        richer.getMetadata().putIfAbsent("merged_from", new ArrayList<>());
        List<String> mergedFrom = (List<String>) richer.getMetadata().get("merged_from");
        if (!mergedFrom.contains(analyzer1)) {
            mergedFrom.add(analyzer1);
        }
        if (!mergedFrom.contains(analyzer2)) {
            mergedFrom.add(analyzer2);
        }

        // 记录合并时间戳
        richer.getMetadata().put("merged_at", Instant.now().toString());

        return richer;
    }

    /**
     * 更新文件索引
     *
     * @param issue 要索引的问题
     */
    private void updateFileIndex(SecurityIssue issue) {
        String filePath = issue.getLocation().getFilePath();
        fileIndex.computeIfAbsent(filePath, k -> new ArrayList<>())
                 .add(issue.getHash());
    }

    /**
     * 获取所有问题
     *
     * @return 不可修改的问题列表
     */
    public List<SecurityIssue> getAllIssues() {
        return new ArrayList<>(issues.values());
    }

    /**
     * 按文件路径查询问题
     *
     * 用于 RefactorCommand：在重构单个文件时，获取该文件的所有已知问题
     *
     * @param filePath 文件路径
     * @return 该文件的问题列表
     */
    public List<SecurityIssue> getIssuesByFile(String filePath) {
        List<String> hashes = fileIndex.getOrDefault(filePath, Collections.emptyList());
        return hashes.stream()
                     .map(issues::get)
                     .filter(Objects::nonNull)
                     .collect(Collectors.toList());
    }

    /**
     * 按行号范围查询问题
     *
     * 用于 AutoFixOrchestrator：修复一个问题时，获取附近的相邻问题
     * 以便在修复时避免冲突或重复修复
     *
     * @param filePath 文件路径
     * @param lineStart 开始行号
     * @param lineEnd 结束行号
     * @return 范围内的问题列表
     */
    public List<SecurityIssue> getIssuesInRange(String filePath, int lineStart, int lineEnd) {
        return getIssuesByFile(filePath).stream()
                .filter(issue -> {
                    int issueLine = issue.getLocation().getLineNumber();
                    return issueLine >= lineStart && issueLine <= lineEnd;
                })
                .collect(Collectors.toList());
    }

    /**
     * 按严重级别查询问题
     *
     * @param severity 严重级别
     * @return 该级别的问题列表
     */
    public List<SecurityIssue> getIssuesBySeverity(IssueSeverity severity) {
        return issues.values().stream()
                     .filter(issue -> issue.getSeverity() == severity)
                     .collect(Collectors.toList());
    }

    /**
     * 按类别查询问题
     *
     * @param category 问题类别
     * @return 该类别的问题列表
     */
    public List<SecurityIssue> getIssuesByCategory(IssueCategory category) {
        return issues.values().stream()
                     .filter(issue -> issue.getCategory() == category)
                     .collect(Collectors.toList());
    }

    /**
     * 按严重级别统计问题数量
     *
     * @return 严重级别 -> 数量映射
     */
    public Map<IssueSeverity, Long> countBySeverity() {
        return issues.values().stream()
                     .collect(Collectors.groupingBy(
                         SecurityIssue::getSeverity,
                         Collectors.counting()
                     ));
    }

    /**
     * 按类别统计问题数量
     *
     * @return 问题类别 -> 数量映射
     */
    public Map<IssueCategory, Long> countByCategory() {
        return issues.values().stream()
                     .collect(Collectors.groupingBy(
                         SecurityIssue::getCategory,
                         Collectors.counting()
                     ));
    }

    /**
     * 获取问题总数
     *
     * @return 问题总数
     */
    public int getTotalIssueCount() {
        return issues.size();
    }

    /**
     * 检查是否存在严重问题
     *
     * @return 如果存在任何 CRITICAL 级别的问题，返回 true
     */
    public boolean hasCriticalIssues() {
        return issues.values().stream()
                     .anyMatch(issue -> issue.getSeverity() == IssueSeverity.CRITICAL);
    }

    /**
     * 将 Store 内容导出为 ScanResult 对象
     *
     * 用途：复用现有的 JsonReportWriter 进行报告生成
     *
     * @param sourcePath 源代码路径
     * @param analyzersUsed 使用的分析器列表
     * @return ScanResult 对象
     */
    public ScanResult toScanResult(String sourcePath, List<String> analyzersUsed) {
        ScanResult.Builder builder = new ScanResult.Builder()
            .sourcePath(sourcePath)
            .startTime(Instant.now())
            .endTime(Instant.now());

        // 添加所有问题
        for (SecurityIssue issue : issues.values()) {
            builder.addIssue(issue);
        }

        // 添加分析器信息
        for (String analyzer : analyzersUsed) {
            builder.addAnalyzer(analyzer);
        }

        // 添加统计信息
        builder.addStatistic("total_issues", issues.size());
        builder.addStatistic("critical_count", issues.values().stream()
            .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL).count());
        builder.addStatistic("high_count", issues.values().stream()
            .filter(i -> i.getSeverity() == IssueSeverity.HIGH).count());
        builder.addStatistic("medium_count", issues.values().stream()
            .filter(i -> i.getSeverity() == IssueSeverity.MEDIUM).count());
        builder.addStatistic("low_count", issues.values().stream()
            .filter(i -> i.getSeverity() == IssueSeverity.LOW).count());
        builder.addStatistic("info_count", issues.values().stream()
            .filter(i -> i.getSeverity() == IssueSeverity.INFO).count());

        return builder.build();
    }

    /**
     * 清空存储
     *
     * 用于会话结束或重新分析时重置 Store
     */
    public synchronized void clear() {
        issues.clear();
        fileIndex.clear();
    }

    /**
     * 获取存储统计信息
     *
     * @return 统计信息字符串
     */
    public String getStatistics() {
        Map<IssueSeverity, Long> bySeverity = countBySeverity();
        int totalCount = getTotalIssueCount();
        int fileCount = fileIndex.size();

        return String.format(
            "Store Statistics: Total=%d issues, Files=%d, Critical=%d, High=%d, Medium=%d, Low=%d, Info=%d",
            totalCount,
            fileCount,
            bySeverity.getOrDefault(IssueSeverity.CRITICAL, 0L),
            bySeverity.getOrDefault(IssueSeverity.HIGH, 0L),
            bySeverity.getOrDefault(IssueSeverity.MEDIUM, 0L),
            bySeverity.getOrDefault(IssueSeverity.LOW, 0L),
            bySeverity.getOrDefault(IssueSeverity.INFO, 0L)
        );
    }

    /**
     * 持久化 Store 到 JSON 文件
     *
     * @param path 文件路径
     * @throws IOException 如果写入失败
     */
    public void saveToDisk(Path path) throws IOException {
        // 使用 ScanResult 导出，然后用 JsonReportWriter 序列化
        ScanResult scanResult = toScanResult(path.getParent().toString(),
                                            issues.values().stream()
                                                  .map(SecurityIssue::getAnalyzer)
                                                  .distinct()
                                                  .collect(Collectors.toList()));

        // 使用 JsonReportWriter 来序列化
        com.harmony.agent.core.report.JsonReportWriter jsonWriter =
            new com.harmony.agent.core.report.JsonReportWriter();
        Files.createDirectories(path.getParent());
        jsonWriter.write(scanResult, path);
    }

    /**
     * 从磁盘加载 Store
     *
     * @param path 文件路径
     * @return 加载的 UnifiedIssueStore 实例
     * @throws IOException 如果读取失败
     */
    public static UnifiedIssueStore loadFromDisk(Path path) throws IOException {
        UnifiedIssueStore store = new UnifiedIssueStore();

        if (!Files.exists(path)) {
            return store; // 返回空 store
        }

        // 使用 JsonReportWriter 读取 ScanResult
        com.harmony.agent.core.report.JsonReportWriter jsonReader =
            new com.harmony.agent.core.report.JsonReportWriter();
        ScanResult scanResult = jsonReader.read(path);

        // 从 ScanResult 中提取 issues 添加到 store
        if (scanResult != null && scanResult.getIssues() != null) {
            store.addIssues(scanResult.getIssues());
        }

        return store;
    }

    /**
     * 用于调试和日志记录
     */
    @Override
    public String toString() {
        return String.format("UnifiedIssueStore[issues=%d, files=%d]",
                           issues.size(),
                           fileIndex.size());
    }
}
