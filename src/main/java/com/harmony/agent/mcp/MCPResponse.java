package com.harmony.agent.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP 响应模型 - 符合 JSON-RPC 2.0 规范
 */
public class MCPResponse {
    private String jsonrpc = "2.0";
    private Object result;
    private MCPError error;
    private String id;

    private static final Gson gson = new Gson();

    public MCPResponse(String id) {
        this.id = id;
    }

    public MCPResponse(String id, Object result) {
        this.id = id;
        this.result = result;
    }

    public MCPResponse(String id, MCPError error) {
        this.id = id;
        this.error = error;
    }

    // 成功响应
    public static MCPResponse success(String id, Object result) {
        return new MCPResponse(id, result);
    }

    // 错误响应
    public static MCPResponse error(String id, int code, String message) {
        return new MCPResponse(id, new MCPError(code, message));
    }

    // 内部错误
    public static MCPResponse internalError(String id, String message) {
        return new MCPResponse(id, new MCPError(-32603, message));
    }

    // 转换为 JSON 字符串
    public String toJSON() {
        JsonObject obj = new JsonObject();
        obj.addProperty("jsonrpc", jsonrpc);

        if (error != null) {
            JsonObject errorObj = new JsonObject();
            errorObj.addProperty("code", error.code);
            errorObj.addProperty("message", error.message);
            obj.add("error", errorObj);
            obj.addProperty("result", (String) null);
        } else {
            obj.add("result", gson.toJsonTree(result));
            obj.add("error", null);
        }

        if (id != null) {
            obj.addProperty("id", id);
        }

        return obj.toString();
    }

    // Getters
    public String getId() {
        return id;
    }

    public Object getResult() {
        return result;
    }

    public MCPError getError() {
        return error;
    }

    public boolean isError() {
        return error != null;
    }

    /**
     * MCP 错误对象
     */
    public static class MCPError {
        public int code;
        public String message;

        public MCPError(int code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public String toString() {
            return code + ": " + message;
        }
    }

    @Override
    public String toString() {
        return toJSON();
    }
}
