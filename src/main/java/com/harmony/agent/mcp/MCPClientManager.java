package com.harmony.agent.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.harmony.agent.llm.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * MCP 客户端管理器
 * 管理多个 MCP 客户端连接，从 JSON 配置文件加载配置并初始化
 *
 * 配置文件格式 (mcp-config.json):
 * {
 *   "mcpServers": [
 *     {
 *       "name": "server-name",
 *       "transport": "stdio|http",
 *       "command": "command to run" (仅 stdio),
 *       "host": "localhost" (仅 http),
 *       "port": 8080 (仅 http)
 *     }
 *   ]
 * }
 */
public class MCPClientManager {
    private static final Logger logger = LoggerFactory.getLogger(MCPClientManager.class);
    private static final Gson gson = new Gson();

    private final Map<String, MCPClient> clients = new HashMap<>();
    private final Map<String, ToolDefinition> allMcpTools = new HashMap<>();
    private final List<String> clientOrder = new ArrayList<>(); // 记录客户端加载顺序，用于工具冲突检测

    /**
     * 从 JSON 配置文件初始化所有 MCP 客户端
     *
     * @param configPath 配置文件路径
     */
    public void loadFromConfig(String configPath) throws Exception {
        logger.info("📋 加载 MCP 配置文件: {}", configPath);

        File configFile = new File(configPath);
        if (!configFile.exists()) {
            throw new FileNotFoundException("配置文件不存在: " + configPath);
        }

        String configContent = new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8);
        JsonObject config = gson.fromJson(configContent, JsonObject.class);

        if (!config.has("mcpServers")) {
            throw new IllegalArgumentException("配置文件缺少 'mcpServers' 字段");
        }

        JsonArray serversArray = config.getAsJsonArray("mcpServers");

        for (var serverElement : serversArray) {
            var serverObj = serverElement.getAsJsonObject();
            initializeClient(serverObj);
        }

        logger.info("✅ 已加载 {} 个 MCP 服务，共 {} 个工具",
            clients.size(), allMcpTools.size());
    }

    /**
     * 初始化单个 MCP 客户端
     */
    private void initializeClient(JsonObject serverConfig) throws Exception {
        String serverName = serverConfig.get("name").getAsString();
        String transport = serverConfig.get("transport").getAsString();

        MCPClient client;

        if ("stdio".equalsIgnoreCase(transport)) {
            if (!serverConfig.has("command")) {
                throw new IllegalArgumentException("Stdio 服务缺少 'command' 字段");
            }
            String command = serverConfig.get("command").getAsString();
            client = MCPClient.createStdioClient(serverName, command);

        } else if ("http".equalsIgnoreCase(transport)) {
            if (!serverConfig.has("host") || !serverConfig.has("port")) {
                throw new IllegalArgumentException("HTTP 服务缺少 'host' 或 'port' 字段");
            }
            String host = serverConfig.get("host").getAsString();
            int port = serverConfig.get("port").getAsInt();
            client = MCPClient.createHttpClient(serverName, host, port);

        } else {
            throw new IllegalArgumentException("未知的传输类型: " + transport);
        }

        try {
            // 连接到 MCP 服务
            client.connect();

            // 注册客户端和其工具
            clients.put(serverName, client);
            clientOrder.add(serverName);

            // 将该客户端的工具添加到全局工具映射，并检测冲突
            for (var tool : client.getTools().values()) {
                if (allMcpTools.containsKey(tool.getName())) {
                    logger.warn("⚠️ 工具冲突: {} (来自 {} 和 {})",
                        tool.getName(), serverName,
                        findToolOwner(tool.getName()));
                } else {
                    allMcpTools.put(tool.getName(), tool);
                }
            }

            logger.info("✅ 客户端已初始化: {} (工具数: {})",
                serverName, client.getTools().size());

        } catch (Exception e) {
            logger.error("❌ 初始化客户端失败: {} - {}", serverName, e.getMessage());
            throw e;
        }
    }

    /**
     * 查找工具的所有者（来自哪个 MCP 服务）
     */
    private String findToolOwner(String toolName) {
        for (String clientName : clientOrder) {
            MCPClient client = clients.get(clientName);
            if (client != null && client.getTools().containsKey(toolName)) {
                return clientName;
            }
        }
        return "unknown";
    }

    /**
     * 获取所有 MCP 工具定义
     */
    public Map<String, ToolDefinition> getAllMcpTools() {
        return new HashMap<>(allMcpTools);
    }

    /**
     * 检查工具是否来自 MCP 服务
     */
    public boolean isMcpTool(String toolName) {
        return allMcpTools.containsKey(toolName);
    }

    /**
     * 通过 MCP 客户端调用工具
     *
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    public String callMcpTool(String toolName, Map<String, Object> arguments) throws Exception {
        if (!isMcpTool(toolName)) {
            throw new IllegalArgumentException("工具不是 MCP 工具: " + toolName);
        }

        // 找到拥有该工具的客户端
        for (String clientName : clientOrder) {
            MCPClient client = clients.get(clientName);
            if (client != null && client.getTools().containsKey(toolName)) {
                logger.info("📞 通过 {} 调用 MCP 工具: {}", clientName, toolName);
                return client.callTool(toolName, arguments);
            }
        }

        throw new Exception("找不到工具的所有者: " + toolName);
    }

    /**
     * 获取所有注册的 MCP 客户端
     */
    public Collection<MCPClient> getAllClients() {
        return new ArrayList<>(clients.values());
    }

    /**
     * 获取特定的 MCP 客户端
     */
    public MCPClient getClient(String serverName) {
        return clients.get(serverName);
    }

    /**
     * 断开所有 MCP 连接
     */
    public void disconnectAll() {
        for (MCPClient client : clients.values()) {
            try {
                client.disconnect();
            } catch (Exception e) {
                logger.warn("⚠️ 断开连接时出错: {}", e.getMessage());
            }
        }
        clients.clear();
        allMcpTools.clear();
        clientOrder.clear();
        logger.info("✅ 所有 MCP 连接已断开");
    }

    /**
     * 获取统计信息
     */
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("MCP 客户端统计:\n");
        sb.append("  - 已连接客户端: ").append(clients.size()).append("\n");
        sb.append("  - 总工具数: ").append(allMcpTools.size()).append("\n");

        for (String clientName : clientOrder) {
            MCPClient client = clients.get(clientName);
            if (client != null) {
                sb.append("  - ").append(clientName)
                    .append(": ").append(client.getTools().size())
                    .append(" 个工具\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "MCPClientManager{" +
                "clients=" + clients.size() +
                ", tools=" + allMcpTools.size() +
                "}";
    }
}
