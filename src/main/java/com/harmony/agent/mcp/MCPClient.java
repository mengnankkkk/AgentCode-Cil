package com.harmony.agent.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.harmony.agent.llm.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MCP 客户端 - 用于连接到远程 MCP 服务并调用工具
 *
 * 工作流程：
 * 1. 建立与远程 MCP 服务的连接（Stdio 或 HTTP）
 * 2. 发送 initialize 请求进行握手
 * 3. 请求 tools/list 获取可用工具列表
 * 4. 当 AI 需要调用工具时，通过 MCP 协议远程执行
 * 5. 返回工具执行结果到 AI
 */
public class MCPClient {
    private static final Logger logger = LoggerFactory.getLogger(MCPClient.class);
    private static final Gson gson = new Gson();

    private final String serviceName;
    private final String transportType; // "stdio" 或 "http"
    private final String command; // 对于 stdio：执行的命令；对于 http：服务 URL
    private final int port; // 仅用于 http
    private final String host; // 仅用于 http

    // Stdio 传输相关
    private Process process;
    private BufferedReader stdoutReader;
    private PrintWriter stdinWriter;

    // HTTP 传输相关
    private String httpBaseUrl;

    // 工具缓存
    private Map<String, ToolDefinition> tools = new HashMap<>();
    private boolean initialized = false;
    private int nextRequestId = 1;

    /**
     * 创建 Stdio 型 MCP 客户端
     */
    public static MCPClient createStdioClient(String serviceName, String command) {
        return new MCPClient(serviceName, "stdio", command, null, 0, null);
    }

    /**
     * 创建 HTTP 型 MCP 客户端
     */
    public static MCPClient createHttpClient(String serviceName, String host, int port) {
        return new MCPClient(serviceName, "http", null, host, port, null);
    }

    private MCPClient(String serviceName, String transportType, String command,
                      String host, int port, String httpUrl) {
        this.serviceName = serviceName;
        this.transportType = transportType;
        this.command = command;
        this.host = host;
        this.port = port;
        this.httpBaseUrl = httpUrl;
    }

    /**
     * 初始化连接并获取工具列表
     */
    public synchronized void connect() throws Exception {
        if (initialized) {
            logger.info("✅ MCP 客户端已连接: {}", serviceName);
            return;
        }

        try {
            if ("stdio".equalsIgnoreCase(transportType)) {
                connectStdio();
            } else if ("http".equalsIgnoreCase(transportType)) {
                connectHttp();
            } else {
                throw new IllegalArgumentException("未知的传输类型: " + transportType);
            }

            // 初始化握手
            performInitialize();

            // 获取工具列表
            loadToolDefinitions();

            initialized = true;
            logger.info("✅ MCP 客户端连接成功: {} ({} 个工具)",
                serviceName, tools.size());

        } catch (Exception e) {
            logger.error("❌ MCP 客户端连接失败: {}", serviceName, e);
            cleanup();
            throw e;
        }
    }

    /**
     * 连接到 Stdio MCP 服务
     */
    private void connectStdio() throws Exception {
        logger.info("🔌 连接到 Stdio MCP 服务: {} (命令: {})", serviceName, command);

        ProcessBuilder pb = new ProcessBuilder(getShellCommand(command));
        pb.redirectErrorStream(true);
        this.process = pb.start();

        this.stdoutReader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.stdinWriter = new PrintWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);

        logger.info("✅ Stdio 连接已建立");
    }

    /**
     * 连接到 HTTP MCP 服务
     */
    private void connectHttp() throws Exception {
        logger.info("🔌 连接到 HTTP MCP 服务: {} ({}:{})", serviceName, host, port);

        this.httpBaseUrl = String.format("http://%s:%d", host, port);

        // 发送一个简单的健康检查
        try {
            String healthUrl = httpBaseUrl + "/health";
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL(healthUrl).openConnection();
            conn.setConnectTimeout(5000);
            int code = conn.getResponseCode();

            if (code != 200) {
                throw new IOException("健康检查失败，状态码: " + code);
            }

            logger.info("✅ HTTP 连接已建立");
        } catch (Exception e) {
            throw new Exception("无法连接到 HTTP MCP 服务: " + e.getMessage(), e);
        }
    }

    /**
     * 执行初始化握手
     */
    private void performInitialize() throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", "initialize");
        request.addProperty("id", String.valueOf(nextRequestId++));

        JsonObject response = sendRequest(request);

        if (response == null || !response.has("result")) {
            throw new Exception("初始化失败：无有效响应");
        }

        logger.info("✅ MCP 握手完成");
    }

    /**
     * 加载工具定义列表
     */
    private void loadToolDefinitions() throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", "tools/list");
        request.addProperty("id", String.valueOf(nextRequestId++));

        JsonObject response = sendRequest(request);

        if (response == null || !response.has("result")) {
            throw new Exception("获取工具列表失败：无有效响应");
        }

        tools.clear();

        var toolsArray = response.getAsJsonObject("result").getAsJsonArray("tools");
        for (var toolElement : toolsArray) {
            var toolObj = toolElement.getAsJsonObject();
            ToolDefinition toolDef = new ToolDefinition();
            toolDef.setName(toolObj.get("name").getAsString());
            toolDef.setDescription(toolObj.get("description").getAsString());

            if (toolObj.has("inputSchema")) {
                toolDef.setParameters(gson.fromJson(toolObj.get("inputSchema"), Map.class));
            }

            tools.put(toolDef.getName(), toolDef);
        }

        logger.info("✅ 已加载 {} 个工具", tools.size());
    }

    /**
     * 调用远程工具
     */
    public synchronized String callTool(String toolName, Map<String, Object> arguments) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("MCP 客户端未初始化，请先调用 connect()");
        }

        if (!tools.containsKey(toolName)) {
            throw new IllegalArgumentException("工具不存在: " + toolName);
        }

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", "tools/call");
        request.addProperty("id", String.valueOf(nextRequestId++));

        JsonObject params = new JsonObject();
        params.addProperty("name", toolName);
        params.add("arguments", gson.toJsonTree(arguments));
        request.add("params", params);

        logger.info("🔧 调用远程工具: {} (参数: {})", toolName, arguments);

        JsonObject response = sendRequest(request);

        if (response == null) {
            throw new Exception("工具调用失败：无有效响应");
        }

        if (response.has("error") && response.get("error") != null) {
            var error = response.getAsJsonObject("error");
            throw new Exception("工具执行失败: " + error.get("message").getAsString());
        }

        if (!response.has("result")) {
            throw new Exception("工具调用失败：响应中无结果");
        }

        var result = response.getAsJsonObject("result");
        var contentArray = result.getAsJsonArray("content");

        if (contentArray == null || contentArray.size() == 0) {
            return "";
        }

        // 提取文本内容
        StringBuilder resultText = new StringBuilder();
        for (var item : contentArray) {
            var contentObj = item.getAsJsonObject();
            if ("text".equals(contentObj.get("type").getAsString())) {
                resultText.append(contentObj.get("text").getAsString());
            }
        }

        logger.info("✅ 工具执行完成: {}", toolName);
        return resultText.toString();
    }

    /**
     * 发送 JSON-RPC 请求并获取响应
     */
    private JsonObject sendRequest(JsonObject request) throws Exception {
        if ("stdio".equalsIgnoreCase(transportType)) {
            return sendRequestStdio(request);
        } else {
            return sendRequestHttp(request);
        }
    }

    /**
     * 通过 Stdio 发送请求
     */
    private JsonObject sendRequestStdio(JsonObject request) throws Exception {
        String requestStr = gson.toJson(request);
        logger.debug("📤 Stdio 请求: {}", requestStr);

        stdinWriter.println(requestStr);
        stdinWriter.flush();

        String responseLine = stdoutReader.readLine();
        if (responseLine == null) {
            throw new IOException("Stdio 连接已关闭");
        }

        logger.debug("📨 Stdio 响应: {}", responseLine);
        return gson.fromJson(responseLine, JsonObject.class);
    }

    /**
     * 通过 HTTP 发送请求
     */
    private JsonObject sendRequestHttp(JsonObject request) throws Exception {
        String requestStr = gson.toJson(request);
        logger.debug("📤 HTTP 请求: {}", requestStr);

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
            new java.net.URL(httpBaseUrl + "/mcp").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestStr.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP 请求失败，状态码: " + responseCode);
        }

        String responseStr;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            responseStr = sb.toString();
        }

        logger.debug("📨 HTTP 响应: {}", responseStr);
        return gson.fromJson(responseStr, JsonObject.class);
    }

    /**
     * 获取该 MCP 服务的所有工具定义
     */
    public Map<String, ToolDefinition> getTools() {
        return new HashMap<>(tools);
    }

    /**
     * 获取该 MCP 服务的名称
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 关闭连接
     */
    public synchronized void disconnect() {
        cleanup();
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        if ("stdio".equalsIgnoreCase(transportType)) {
            if (stdinWriter != null) {
                stdinWriter.close();
            }
            if (stdoutReader != null) {
                try {
                    stdoutReader.close();
                } catch (IOException e) {
                    logger.warn("⚠️ 关闭 Stdio 读取器时出错: {}", e.getMessage());
                }
            }
            if (process != null) {
                process.destroyForcibly();
            }
        }

        initialized = false;
        tools.clear();
        logger.info("✅ MCP 客户端已断开: {}", serviceName);
    }

    /**
     * 获取平台相关的 shell 命令
     */
    private String[] getShellCommand(String command) {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return new String[]{"cmd", "/c", command};
        } else {
            return new String[]{"/bin/bash", "-c", command};
        }
    }

    @Override
    public String toString() {
        return String.format("MCPClient{name='%s', transport='%s', tools=%d}",
            serviceName, transportType, tools.size());
    }
}
