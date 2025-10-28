package com.harmony.agent.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

/**
 * MCP 请求模型 - 符合 JSON-RPC 2.0 规范
 */
public class MCPRequest {
    private String jsonrpc = "2.0";
    private String method;
    private Map<String, Object> params;
    private String id;

    // 构造函数
    public MCPRequest(String method, Map<String, Object> params, String id) {
        this.method = method;
        this.params = params;
        this.id = id;
    }

    // 从 JSON 字符串解析
    public static MCPRequest fromJSON(String jsonStr) {
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            String method = obj.get("method").getAsString();
            String id = obj.has("id") ? obj.get("id").getAsString() : null;
            // 简化处理，实际使用 GSON 的更完整反序列化
            return new MCPRequest(method, null, id);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 MCP 请求: " + e.getMessage());
        }
    }

    // Getters
    public String getMethod() {
        return method;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public String getId() {
        return id;
    }

    // 便捷方法：获取工具名称（用于 tools/call 请求）
    public String getToolName() {
        if (params != null && params.containsKey("name")) {
            return params.get("name").toString();
        }
        return null;
    }

    // 便捷方法：获取工具参数（用于 tools/call 请求）
    @SuppressWarnings("unchecked")
    public Map<String, Object> getToolArguments() {
        if (params != null && params.containsKey("arguments")) {
            return (Map<String, Object>) params.get("arguments");
        }
        return null;
    }

    @Override
    public String toString() {
        return "MCPRequest{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", method='" + method + '\'' +
                ", id='" + id + '\'' +
                ", params=" + params +
                '}';
    }
}
