package com.harmony.agent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Advanced Task Router
 * Intelligently route tasks to: Roles (LLM) + Local Tools + MCP Tools + Commands
 */
public class AdvancedTaskRouter {
    private static final Logger logger = LoggerFactory.getLogger(AdvancedTaskRouter.class);

    public enum ExecutionType {
        ROLE,        // LLM role (Planner, Coder, Reviewer, Analyzer)
        LOCAL_TOOL,  // Local tool (compile, test, analyze, etc.)
        MCP_TOOL,    // MCP remote tool
        COMMAND,     // CLI command (/analyze, /review, etc.)
        UNKNOWN      // Unknown type (fallback)
    }

    public enum RouteDecision {
        LLM_PLANNER,
        LLM_CODER,
        LLM_REVIEWER,
        LLM_ANALYZER,
        TOOL_COMPILE,
        TOOL_TEST,
        TOOL_ANALYZE,
        MCP_SEARCH,
        MCP_FILE_READ,
        CMD_ANALYZE,
        CMD_REVIEW,
        UNKNOWN
    }

    /**
     * Route task based on description and context
     */
    public static RouteDecision route(String taskDescription, TaskExecutionContext context) {
        String lower = taskDescription.toLowerCase();

        // Design and planning tasks
        if (lower.contains("design") || lower.contains("plan") ||
            lower.contains("architect") || lower.contains("strategy") ||
            lower.contains("设计") || lower.contains("规划") || lower.contains("架构")) {
            return RouteDecision.LLM_PLANNER;
        }

        // Code implementation tasks
        if (lower.contains("implement") || lower.contains("write code") ||
            lower.contains("develop") || lower.contains("create") ||
            lower.contains("编写") || lower.contains("实现") || lower.contains("开发")) {
            return RouteDecision.LLM_CODER;
        }

        // Code review and verification tasks
        if (lower.contains("review") || lower.contains("verify") ||
            lower.contains("check") || lower.contains("audit") ||
            lower.contains("审查") || lower.contains("验证")) {
            return RouteDecision.LLM_REVIEWER;
        }

        // Analysis tasks
        if (lower.contains("analyze") || lower.contains("identify") ||
            lower.contains("find") || lower.contains("detect") ||
            lower.contains("分析") || lower.contains("识别")) {
            return RouteDecision.LLM_ANALYZER;
        }

        // Compilation/Build tasks
        if (lower.contains("compile") || lower.contains("build") ||
            lower.contains("编译") || lower.contains("构建")) {
            return RouteDecision.TOOL_COMPILE;
        }

        // Testing tasks
        if (lower.contains("test") || lower.contains("run test") ||
            lower.contains("验证功能") || lower.contains("测试")) {
            return RouteDecision.TOOL_TEST;
        }

        // Local analysis tasks
        if (lower.contains("static analyze") || lower.contains("lint") ||
            lower.contains("静态分析")) {
            return RouteDecision.TOOL_ANALYZE;
        }

        // Default: assume LLM analysis
        return RouteDecision.LLM_ANALYZER;
    }

    /**
     * Get execution type for a route decision
     */
    public static ExecutionType getExecutionType(RouteDecision route) {
        return switch (route) {
            case LLM_PLANNER, LLM_CODER, LLM_REVIEWER, LLM_ANALYZER -> ExecutionType.ROLE;
            case TOOL_COMPILE, TOOL_TEST, TOOL_ANALYZE -> ExecutionType.LOCAL_TOOL;
            case MCP_SEARCH, MCP_FILE_READ -> ExecutionType.MCP_TOOL;
            case CMD_ANALYZE, CMD_REVIEW -> ExecutionType.COMMAND;
            default -> ExecutionType.UNKNOWN;
        };
    }

    /**
     * Get LLM role name for route decision
     */
    public static String getLLMRole(RouteDecision route) {
        return switch (route) {
            case LLM_PLANNER -> "planner";
            case LLM_CODER -> "coder";
            case LLM_REVIEWER -> "reviewer";
            case LLM_ANALYZER -> "analyzer";
            default -> null;
        };
    }

    /**
     * Get local tool name
     */
    public static String getLocalTool(RouteDecision route) {
        return switch (route) {
            case TOOL_COMPILE -> "compile";
            case TOOL_TEST -> "test";
            case TOOL_ANALYZE -> "analyze";
            default -> null;
        };
    }

    /**
     * Get MCP tool name
     */
    public static String getMCPTool(RouteDecision route) {
        return switch (route) {
            case MCP_SEARCH -> "web_search";
            case MCP_FILE_READ -> "file_read";
            default -> null;
        };
    }

    /**
     * Get CLI command
     */
    public static String getCLICommand(RouteDecision route) {
        return switch (route) {
            case CMD_ANALYZE -> "/analyze";
            case CMD_REVIEW -> "/review";
            default -> null;
        };
    }

    /**
     * Get routing explanation
     */
    public static String getRoutingExplanation(RouteDecision route) {
        return switch (route) {
            case LLM_PLANNER -> "Routing to Planner role (LLM) for design/planning";
            case LLM_CODER -> "Routing to Coder role (LLM) for implementation";
            case LLM_REVIEWER -> "Routing to Reviewer role (LLM) for code review";
            case LLM_ANALYZER -> "Routing to Analyzer role (LLM) for analysis";
            case TOOL_COMPILE -> "Routing to local compile tool";
            case TOOL_TEST -> "Routing to local test tool";
            case TOOL_ANALYZE -> "Routing to local analysis tool";
            case MCP_SEARCH -> "Routing to MCP web search tool";
            case MCP_FILE_READ -> "Routing to MCP file read tool";
            case CMD_ANALYZE -> "Routing to /analyze command";
            case CMD_REVIEW -> "Routing to /review command";
            default -> "Unknown routing";
        };
    }

    /**
     * Check if task requires external tools
     */
    public static boolean requiresExternalTools(RouteDecision route) {
        return switch (route) {
            case MCP_SEARCH, MCP_FILE_READ -> true;
            default -> false;
        };
    }

    /**
     * Check if task requires local tools
     */
    public static boolean requiresLocalTools(RouteDecision route) {
        return switch (route) {
            case TOOL_COMPILE, TOOL_TEST, TOOL_ANALYZE -> true;
            default -> false;
        };
    }
}
