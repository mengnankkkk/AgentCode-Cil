package com.harmony.agent.strategic;

import com.harmony.agent.tools.result.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T1.1 Security Scoring Service
 * 计算代码模块的安全评分 (0-100分)
 */
public class SecurityScoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityScoringService.class);
    
    // 基础分数
    private static final int BASE_SCORE = 100;
    
    // 严重性权重
    private static final Map<String, Integer> SEVERITY_WEIGHTS = Map.of(
        "CRITICAL", 25,
        "HIGH", 15,
        "MEDIUM", 8,
        "LOW", 3
    );
    
    // 漏洞类型权重
    private static final Map<String, Double> VULNERABILITY_TYPE_WEIGHTS = Map.of(
        "BUFFER_OVERFLOW", 1.5,
        "MEMORY_LEAK", 1.2,
        "NULL_POINTER", 1.0,
        "RESOURCE_LEAK", 0.8,
        "DEAD_CODE", 0.3
    );
    
    /**
     * 计算单个文件的安全评分
     */
    public SecurityScore calculateScore(String filePath, AnalysisResult analysisResult) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                logger.warn("File not found: {}", filePath);
                return new SecurityScore(filePath, 0, "FILE_NOT_FOUND");
            }
            
            // 基础评分
            int score = BASE_SCORE;
            int criticalCount = 0;
            int highCount = 0;
            int mediumCount = 0;
            int lowCount = 0;
            
            // 统计该文件的问题
            List<AnalysisResult.Bug> fileBugs = analysisResult.getBugs().stream()
                .filter(bug -> bug.getFile().equals(filePath) || bug.getFile().endsWith(file.getName()))
                .toList();
            
            for (AnalysisResult.Bug bug : fileBugs) {
                String severity = mapPriorityToSeverity(bug.getPriority());
                int deduction = SEVERITY_WEIGHTS.getOrDefault(severity, 5);
                
                // 根据漏洞类型调整扣分
                String vulnType = mapBugTypeToVulnerability(bug.getType());
                double multiplier = VULNERABILITY_TYPE_WEIGHTS.getOrDefault(vulnType, 1.0);
                
                score -= (int) (deduction * multiplier);
                
                // 统计数量
                switch (severity) {
                    case "CRITICAL" -> criticalCount++;
                    case "HIGH" -> highCount++;
                    case "MEDIUM" -> mediumCount++;
                    case "LOW" -> lowCount++;
                }
            }
            
            // 代码复杂度惩罚
            int complexityPenalty = calculateComplexityPenalty(file);
            score -= complexityPenalty;
            
            // 确保分数在0-100范围内
            score = Math.max(0, Math.min(100, score));
            
            String riskLevel = determineRiskLevel(score);
            
            return new SecurityScore(filePath, score, riskLevel, 
                criticalCount, highCount, mediumCount, lowCount, complexityPenalty);
            
        } catch (Exception e) {
            logger.error("Failed to calculate security score for {}: {}", filePath, e.getMessage());
            return new SecurityScore(filePath, 0, "CALCULATION_ERROR");
        }
    }
    
    /**
     * 将SpotBugs优先级映射到严重性级别
     */
    private String mapPriorityToSeverity(String priority) {
        return switch (priority.toUpperCase()) {
            case "1", "HIGH" -> "CRITICAL";
            case "2", "MEDIUM" -> "HIGH";
            case "3", "LOW" -> "MEDIUM";
            default -> "LOW";
        };
    }
    
    /**
     * 将Bug类型映射到漏洞类型
     */
    private String mapBugTypeToVulnerability(String bugType) {
        if (bugType.contains("BUFFER") || bugType.contains("OVERFLOW")) {
            return "BUFFER_OVERFLOW";
        } else if (bugType.contains("MEMORY") || bugType.contains("LEAK")) {
            return "MEMORY_LEAK";
        } else if (bugType.contains("NULL") || bugType.contains("POINTER")) {
            return "NULL_POINTER";
        } else if (bugType.contains("RESOURCE")) {
            return "RESOURCE_LEAK";
        } else if (bugType.contains("DEAD") || bugType.contains("UNUSED")) {
            return "DEAD_CODE";
        }
        return "OTHER";
    }
    
    /**
     * 计算代码复杂度惩罚
     */
    private int calculateComplexityPenalty(File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            int lineCount = lines.size();
            
            // 基于行数的简单复杂度评估
            if (lineCount > 1000) {
                return 10; // 大文件惩罚
            } else if (lineCount > 500) {
                return 5;
            }
            
            // 检查复杂的控制结构
            int complexityScore = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains("goto") || trimmed.contains("setjmp") || trimmed.contains("longjmp")) {
                    complexityScore += 3; // 危险的控制流
                }
                if (trimmed.matches(".*\\bif\\s*\\(.*&&.*\\|\\|.*\\).*")) {
                    complexityScore += 1; // 复杂条件
                }
            }
            
            return Math.min(15, complexityScore); // 最多扣15分
            
        } catch (IOException e) {
            logger.warn("Failed to read file for complexity analysis: {}", file.getPath());
            return 0;
        }
    }
    
    /**
     * 根据分数确定风险级别
     */
    private String determineRiskLevel(int score) {
        if (score >= 80) {
            return "LOW_RISK";
        } else if (score >= 60) {
            return "MEDIUM_RISK";
        } else if (score >= 40) {
            return "HIGH_RISK";
        } else {
            return "CRITICAL_RISK";
        }
    }
    
    /**
     * 安全评分结果
     */
    public static class SecurityScore {
        private final String filePath;
        private final int score;
        private final String riskLevel;
        private final int criticalCount;
        private final int highCount;
        private final int mediumCount;
        private final int lowCount;
        private final int complexityPenalty;
        
        public SecurityScore(String filePath, int score, String riskLevel) {
            this(filePath, score, riskLevel, 0, 0, 0, 0, 0);
        }
        
        public SecurityScore(String filePath, int score, String riskLevel, 
                           int criticalCount, int highCount, int mediumCount, int lowCount, 
                           int complexityPenalty) {
            this.filePath = filePath;
            this.score = score;
            this.riskLevel = riskLevel;
            this.criticalCount = criticalCount;
            this.highCount = highCount;
            this.mediumCount = mediumCount;
            this.lowCount = lowCount;
            this.complexityPenalty = complexityPenalty;
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public int getScore() { return score; }
        public String getRiskLevel() { return riskLevel; }
        public int getCriticalCount() { return criticalCount; }
        public int getHighCount() { return highCount; }
        public int getMediumCount() { return mediumCount; }
        public int getLowCount() { return lowCount; }
        public int getComplexityPenalty() { return complexityPenalty; }
        public int getTotalIssues() { return criticalCount + highCount + mediumCount + lowCount; }
        
        public String getFileName() {
            return new File(filePath).getName();
        }
        
        @Override
        public String toString() {
            return String.format("%s: %d/100 (%s)", getFileName(), score, riskLevel);
        }
    }
}