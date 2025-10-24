package com.harmony.agent.strategic;

import com.harmony.agent.strategic.SecurityScoringService.SecurityScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * T1.2 Triage Advisor
 * 基于安全评分提供战略建议
 */
public class TriageAdvisor {
    
    private static final Logger logger = LoggerFactory.getLogger(TriageAdvisor.class);
    
    // 决策阈值
    private static final int REWRITE_THRESHOLD = 40;  // 低于40分建议重写
    private static final int REPAIR_THRESHOLD = 70;   // 40-70分建议修复
    // 高于70分建议监控
    
    /**
     * 为单个模块提供分诊建议
     */
    public TriageRecommendation recommend(SecurityScore score) {
        String module = score.getFileName();
        int securityScore = score.getScore();
        
        RecommendationType recommendation;
        String reasoning;
        Priority priority;
        
        if (securityScore < REWRITE_THRESHOLD) {
            // 严重风险 - 建议重写
            recommendation = RecommendationType.REWRITE_RUST;
            reasoning = String.format(
                "安全评分过低 (%d/100)，存在 %d 个严重问题。建议使用 Rust 重写以获得内存安全保障。",
                securityScore, score.getCriticalCount() + score.getHighCount()
            );
            priority = Priority.CRITICAL;
            
        } else if (securityScore < REPAIR_THRESHOLD) {
            // 中等风险 - 建议修复
            recommendation = RecommendationType.REPAIR;
            reasoning = String.format(
                "安全评分中等 (%d/100)，发现 %d 个关键问题。建议立即修复现有漏洞。",
                securityScore, score.getCriticalCount()
            );
            priority = score.getCriticalCount() > 0 ? Priority.HIGH : Priority.MEDIUM;
            
        } else {
            // 低风险 - 建议监控
            recommendation = RecommendationType.MONITOR;
            reasoning = String.format(
                "安全评分良好 (%d/100)，仅有 %d 个轻微问题。建议持续监控。",
                securityScore, score.getTotalIssues()
            );
            priority = Priority.LOW;
        }
        
        return new TriageRecommendation(module, recommendation, reasoning, priority, score);
    }
    
    /**
     * 为多个模块提供批量分诊建议
     */
    public List<TriageRecommendation> recommendBatch(List<SecurityScore> scores) {
        return scores.stream()
            .map(this::recommend)
            .sorted((a, b) -> {
                // 按优先级排序：CRITICAL > HIGH > MEDIUM > LOW
                int priorityCompare = a.getPriority().ordinal() - b.getPriority().ordinal();
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                // 相同优先级按分数排序（分数越低越优先）
                return Integer.compare(a.getSecurityScore().getScore(), b.getSecurityScore().getScore());
            })
            .toList();
    }
    
    /**
     * 生成战略摘要
     */
    public StrategicSummary generateSummary(List<TriageRecommendation> recommendations) {
        int totalModules = recommendations.size();
        int rewriteCount = 0;
        int repairCount = 0;
        int monitorCount = 0;
        int criticalIssues = 0;
        
        for (TriageRecommendation rec : recommendations) {
            switch (rec.getRecommendation()) {
                case REWRITE_RUST -> rewriteCount++;
                case REPAIR -> repairCount++;
                case MONITOR -> monitorCount++;
            }
            criticalIssues += rec.getSecurityScore().getCriticalCount();
        }
        
        return new StrategicSummary(totalModules, rewriteCount, repairCount, monitorCount, criticalIssues);
    }
    
    /**
     * 建议类型枚举
     */
    public enum RecommendationType {
        REWRITE_RUST("使用 Rust 重写"),
        REPAIR("自动修复"),
        MONITOR("持续监控");
        
        private final String description;
        
        RecommendationType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 优先级枚举
     */
    public enum Priority {
        CRITICAL, HIGH, MEDIUM, LOW
    }
    
    /**
     * 分诊建议结果
     */
    public static class TriageRecommendation {
        private final String module;
        private final RecommendationType recommendation;
        private final String reasoning;
        private final Priority priority;
        private final SecurityScore securityScore;
        
        public TriageRecommendation(String module, RecommendationType recommendation, 
                                  String reasoning, Priority priority, SecurityScore securityScore) {
            this.module = module;
            this.recommendation = recommendation;
            this.reasoning = reasoning;
            this.priority = priority;
            this.securityScore = securityScore;
        }
        
        // Getters
        public String getModule() { return module; }
        public RecommendationType getRecommendation() { return recommendation; }
        public String getReasoning() { return reasoning; }
        public Priority getPriority() { return priority; }
        public SecurityScore getSecurityScore() { return securityScore; }
        
        /**
         * 生成JSON格式的决策结果
         */
        public String toJson() {
            return String.format(
                "{\"module\": \"%s\", \"recommendation\": \"%s\", \"score\": %d, \"priority\": \"%s\"}",
                module, recommendation.name(), securityScore.getScore(), priority.name()
            );
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s (%d/100分): %s", 
                priority.name(), module, securityScore.getScore(), recommendation.getDescription());
        }
    }
    
    /**
     * 战略摘要
     */
    public static class StrategicSummary {
        private final int totalModules;
        private final int rewriteCount;
        private final int repairCount;
        private final int monitorCount;
        private final int criticalIssues;
        
        public StrategicSummary(int totalModules, int rewriteCount, int repairCount, 
                              int monitorCount, int criticalIssues) {
            this.totalModules = totalModules;
            this.rewriteCount = rewriteCount;
            this.repairCount = repairCount;
            this.monitorCount = monitorCount;
            this.criticalIssues = criticalIssues;
        }
        
        // Getters
        public int getTotalModules() { return totalModules; }
        public int getRewriteCount() { return rewriteCount; }
        public int getRepairCount() { return repairCount; }
        public int getMonitorCount() { return monitorCount; }
        public int getCriticalIssues() { return criticalIssues; }
        
        @Override
        public String toString() {
            return String.format(
                "分析了 %d 个模块：%d 个需要重写，%d 个需要修复，%d 个需要监控。共发现 %d 个严重问题。",
                totalModules, rewriteCount, repairCount, monitorCount, criticalIssues
            );
        }
    }
}