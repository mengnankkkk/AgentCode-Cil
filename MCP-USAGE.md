# MCP 服务器使用文档

## 概述

HarmonySafeAgent 现在支持通过 **Model Context Protocol (MCP)** 与 Claude API 集成。MCP 是 Anthropic 标准协议，允许 Claude 调用外部工具和资源。

本实现使用 **Plan 1 - MCP 服务器架构**，通过标准 JSON-RPC 2.0 协议暴露所有 19 个 AI 工具。

## 支持的工具（19 个）

### 文件操作（5 个）
1. **read_file** - 读取文件内容
2. **write_file** - 写入文件内容
3. **append_file** - 追加文件内容
4. **search** - 搜索目录中的文件
5. **grep** - 在文件中搜索文本

### 系统命令（7 个）
6. **pwd** - 获取当前工作目录
7. **cd** - 切换工作目录
8. **ls** - 列出目录内容
9. **cat** - 查看文件内容
10. **mkdir** - 创建目录
11. **rm** - 删除文件或目录
12. **shell_exec** - 执行任意 shell 命令

### 专业工具（7 个）
13. **cargo** - Rust 包管理和构建
14. **gcc** - C/C++ 编译
15. **git_status** - 查看 Git 状态
16. **git_diff** - 查看文件变更
17. **git_apply** - 应用 patch 文件
18. **git_commit** - 提交代码
19. **verify_code** - 代码验证（编译/测试/检查）

## 架构

```
Claude API
    ↓
MCP 协议（JSON-RPC 2.0）
    ↓
MCPServerLauncher（启动器）
    ↓
├─ MCPStdioTransport（标准 IO）← 用于与 Claude API 集成
├─ MCPHttpTransport（HTTP）     ← 用于网络部署
    ↓
MCPServer（核心实现）
    ↓
LLMOrchestrator（工具执行引擎）
    ↓
19 个 AI 工具
```

## 启动方式

### 方式 1：Stdio 传输（推荐用于 Claude API）

#### Linux/macOS
```bash
./start-mcp-stdio.sh
```

#### Windows
```cmd
start-mcp-stdio.bat
```

#### 手动启动
```bash
mvn exec:java -Dexec.mainClass="com.harmony.agent.mcp.MCPServerLauncher" \
              -Dexec.args="--transport=stdio"
```

**说明**：
- 从标准输入读取 JSON-RPC 请求
- 将响应写入标准输出
- 用于与 Claude API 或其他 stdio 客户端集成

### 方式 2：HTTP 传输（网络部署）

#### Linux/macOS
```bash
./start-mcp-http.sh 8080 0.0.0.0
```

#### Windows
```cmd
start-mcp-http.bat 8080 0.0.0.0
```

#### 手动启动
```bash
mvn exec:java -Dexec.mainClass="com.harmony.agent.mcp.MCPServerLauncher" \
              -Dexec.args="--transport=http --host=0.0.0.0 --port=8080"
```

**说明**：
- 启动 HTTP 服务器
- 默认端口：8080
- 默认地址：localhost
- 支持远程访问

## JSON-RPC 2.0 协议

所有请求和响应都遵循 JSON-RPC 2.0 规范。

### 请求格式

```json
{
  "jsonrpc": "2.0",
  "method": "初始化|tools/list|tools/call|resources/list|resources/read",
  "params": {
    ...
  },
  "id": "请求ID"
}
```

### 响应格式

成功响应：
```json
{
  "jsonrpc": "2.0",
  "result": { ... },
  "error": null,
  "id": "请求ID"
}
```

错误响应：
```json
{
  "jsonrpc": "2.0",
  "result": null,
  "error": {
    "code": 错误代码,
    "message": "错误描述"
  },
  "id": "请求ID"
}
```

## MCP 方法

### 1. initialize - 初始化连接

**请求**：
```json
{
  "jsonrpc": "2.0",
  "method": "initialize",
  "id": "1"
}
```

**响应**：
```json
{
  "jsonrpc": "2.0",
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": true,
      "resources": true,
      "prompts": false
    },
    "serverInfo": {
      "name": "harmony-safe-agent-mcp",
      "version": "1.0.0"
    }
  },
  "id": "1"
}
```

### 2. tools/list - 列出可用工具

**请求**：
```json
{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "id": "2"
}
```

**响应**：
```json
{
  "jsonrpc": "2.0",
  "result": {
    "tools": [
      {
        "name": "read_file",
        "description": "读取文件内容，用于获取代码、文档等文件的内容进行分析",
        "inputSchema": {
          "type": "object",
          "properties": {
            "path": {
              "type": "string",
              "description": "文件路径"
            }
          },
          "required": ["path"]
        }
      },
      ...
    ]
  },
  "id": "2"
}
```

### 3. tools/call - 调用工具

**请求**：
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "read_file",
    "arguments": {
      "path": "/path/to/file.txt"
    }
  },
  "id": "3"
}
```

**响应**：
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "文件内容..."
      }
    ],
    "isError": false
  },
  "id": "3"
}
```

### 4. resources/list - 列出可用资源

**请求**：
```json
{
  "jsonrpc": "2.0",
  "method": "resources/list",
  "id": "4"
}
```

### 5. resources/read - 读取资源

**请求**：
```json
{
  "jsonrpc": "2.0",
  "method": "resources/read",
  "params": {
    "uri": "harmony://tools"
  },
  "id": "5"
}
```

## 测试示例

### 使用 curl 测试 HTTP 端点

#### 1. 初始化
```bash
curl -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "initialize",
    "id": "1"
  }' | jq
```

#### 2. 列出工具
```bash
curl -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": "2"
  }' | jq '.result.tools | length'
```

#### 3. 调用工具 (pwd)
```bash
curl -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "pwd",
      "arguments": {}
    },
    "id": "3"
  }' | jq
```

#### 4. 调用工具 (read_file)
```bash
curl -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "read_file",
      "arguments": {
        "path": "pom.xml"
      }
    },
    "id": "4"
  }' | jq
```

#### 5. 健康检查
```bash
curl http://localhost:8080/health | jq
```

## 与 Claude API 集成

要将 MCP 服务器与 Claude API 集成，请使用 Stdio 传输方式，并按照 Claude 文档配置 `claude_desktop_config.json`：

```json
{
  "mcpServers": {
    "harmony-safe-agent": {
      "command": "bash",
      "args": ["./start-mcp-stdio.sh"],
      "cwd": "/path/to/HarmonySafeAgent"
    }
  }
}
```

## 配置文件说明

### pom.xml 需要的依赖

已在项目中包含：
- `org.slf4j:slf4j-api` - 日志框架
- `ch.qos.logback:logback-classic` - 日志实现
- `com.google.code.gson:gson` - JSON 处理

## 故障排除

### 问题 1：端口已被占用

**症状**：`Address already in use`

**解决**：
```bash
# 改变端口
./start-mcp-http.sh 9090

# 或杀死占用的进程
lsof -i :8080
kill -9 <PID>
```

### 问题 2：权限错误

**症状**：`Permission denied`

**解决**：
```bash
# 赋予脚本执行权限
chmod +x start-mcp-stdio.sh
chmod +x start-mcp-http.sh
```

### 问题 3：GSON 依赖错误

**症状**：`JsonObject not found`

**解决**：
```bash
# 重新编译并刷新依赖
mvn clean dependency:resolve compile
```

## 代码文件位置

所有 MCP 相关代码位于 `src/main/java/com/harmony/agent/mcp/`：

- `MCPRequest.java` - JSON-RPC 2.0 请求模型
- `MCPResponse.java` - JSON-RPC 2.0 响应模型
- `MCPServer.java` - 核心 MCP 服务器实现（19 个工具）
- `MCPStdioTransport.java` - 标准 IO 传输层
- `MCPHttpTransport.java` - HTTP 传输层
- `MCPServerLauncher.java` - 主启动器

## 性能考虑

- **并发处理**：HTTP 传输使用 Java HttpServer 的默认单线程执行器，可根据需要配置多线程
- **超时设置**：各工具有不同的超时限制（见下表）
- **资源限制**：搜索操作限制为 100 个文件以避免过载

### 工具超时设置

| 工具 | 超时 |
|------|------|
| shell_exec | 30 秒 |
| cargo | 300 秒 |
| gcc | 60 秒 |
| git_* | 10-30 秒 |
| verify_code | 120 秒 |
| 其他 | 无限 |

## 日志配置

日志配置位于 `src/main/resources/logback.xml`。

默认日志级别：
- ROOT: INFO
- 特定包: DEBUG（可根据需要调整）

## 后续改进

- [ ] 支持 WebSocket 传输
- [ ] 添加身份验证和授权
- [ ] 实现基于令牌的速率限制
- [ ] 支持多工具批量调用
- [ ] 实现工具调用缓存
- [ ] 添加性能监测和指标

## 许可证

根据项目许可证规定。
