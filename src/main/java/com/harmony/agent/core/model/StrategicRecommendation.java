package com.harmony.agent.core.model;

/**
 * Strategic recommendation for a module based on security analysis
 * Part of P-Strategic (T1.2 - Triage)
 */
public class StrategicRecommendation {

    /**
     * Recommendation types based on security score and issue severity
     */
    public enum RecommendationType {
        /**
         * Rewrite in Rust - for modules with very low security scores (<40)
         * and high density of memory safety issues
         */
        REWRITE_RUST("Rust重写", "使用Rust重写以实现天然内存安全", "🦀"),

        /**
         * Auto-repair - for modules with critical issues but decent structure (40-70)
         */
        REPAIR("自动修复", "立即修复严重安全问题", "🔧"),

        /**
         * Refactor - for modules with maintainability issues but not critical security risks
         */
        REFACTOR("重构优化", "改善代码质量和可维护性", "📐"),

        /**
         * Monitor - for modules with good security scores (>70) and only minor issues
         */
        MONITOR("持续监控", "定期检查，暂无紧急行动", "👁️");

        private final String displayName;
        private final String description;
        private final String icon;

        RecommendationType(String displayName, String description, String icon) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }
    }

    /**
     * Risk level based on security score
     */
    public enum RiskLevel {
        CRITICAL("严重", "🔴", 0, 30),
        HIGH("高风险", "🟠", 30, 50),
        MEDIUM("中风险", "🟡", 50, 70),
        LOW("低风险", "🟢", 70, 100);

        private final String displayName;
        private final String icon;
        private final int minScore;
        private final int maxScore;

        RiskLevel(String displayName, String icon, int minScore, int maxScore) {
            this.displayName = displayName;
            this.icon = icon;
            this.minScore = minScore;
            this.maxScore = maxScore;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }

        public static RiskLevel fromScore(int score) {
            if (score < 30) return CRITICAL;
            if (score < 50) return HIGH;
            if (score < 70) return MEDIUM;
            return LOW;
        }
    }

    private final String moduleName;
    private final int securityScore;  // 0-100
    private final RiskLevel riskLevel;
    private final RecommendationType recommendation;
    private final int criticalCount;
    private final int highCount;
    private final int totalIssues;
    private final String rationale;  // Why this recommendation

    private StrategicRecommendation(Builder builder) {
        this.moduleName = builder.moduleName;
        this.securityScore = builder.securityScore;
        this.riskLevel = RiskLevel.fromScore(securityScore);
        this.recommendation = builder.recommendation;
        this.criticalCount = builder.criticalCount;
        this.highCount = builder.highCount;
        this.totalIssues = builder.totalIssues;
        this.rationale = builder.rationale;
    }

    public String getModuleName() {
        return moduleName;
    }

    public int getSecurityScore() {
        return securityScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public RecommendationType getRecommendation() {
        return recommendation;
    }

    public int getCriticalCount() {
        return criticalCount;
    }

    public int getHighCount() {
        return highCount;
    }

    public int getTotalIssues() {
        return totalIssues;
    }

    public String getRationale() {
        return rationale;
    }

    /**
     * Get a formatted summary string
     */
    public String getSummary() {
        return String.format("[%s %s] %s (%d/100分): %s %s - %s",
            riskLevel.getIcon(),
            riskLevel.getDisplayName(),
            moduleName,
            securityScore,
            recommendation.getIcon(),
            recommendation.getDisplayName(),
            rationale
        );
    }

    @Override
    public String toString() {
        return getSummary();
    }

    public static class Builder {
        private String moduleName;
        private int securityScore;
        private RecommendationType recommendation;
        private int criticalCount;
        private int highCount;
        private int totalIssues;
        private String rationale;

        public Builder moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public Builder securityScore(int score) {
            this.securityScore = Math.max(0, Math.min(100, score));
            return this;
        }

        public Builder recommendation(RecommendationType recommendation) {
            this.recommendation = recommendation;
            return this;
        }

        public Builder criticalCount(int count) {
            this.criticalCount = count;
            return this;
        }

        public Builder highCount(int count) {
            this.highCount = count;
            return this;
        }

        public Builder totalIssues(int count) {
            this.totalIssues = count;
            return this;
        }

        public Builder rationale(String rationale) {
            this.rationale = rationale;
            return this;
        }

        public StrategicRecommendation build() {
            if (moduleName == null || moduleName.isEmpty()) {
                throw new IllegalStateException("Module name is required");
            }
            if (recommendation == null) {
                throw new IllegalStateException("Recommendation type is required");
            }
            if (rationale == null || rationale.isEmpty()) {
                throw new IllegalStateException("Rationale is required");
            }
            return new StrategicRecommendation(this);
        }
    }
}
