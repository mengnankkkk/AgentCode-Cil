# MCP 客户端集成指南

## 概述

HarmonySafeAgent 现在支持通过 **MCP 客户端** 调用远程 MCP 服务的工具。这允许你的本地 AI 能够：

1. 连接到远程 MCP 服务器
2. 动态加载远程服务暴露的工具定义
3. 在 AI 执行任务时，自动识别并调用远程工具
4. 将远程工具的结果返回给 AI 用于进一步处理

## 架构

```
本地 AI (LLMOrchestrator)
    ↓
识别工具类型（本地或 MCP）
    ↓
├─ 本地工具 → 直接执行（文件操作、shell 等）
└─ MCP 工具 → MCPClientManager → MCPClient → 远程 MCP 服务
                                              ↓
                                          执行并返回结果
```

## 配置文件

### 配置文件位置

`mcp-config.json` - 项目根目录

### 配置文件格式

```json
{
  "mcpServers": [
    {
      "name": "服务名称",
      "transport": "stdio|http",
      // Stdio 传输特定配置
      "command": "执行该服务的命令",
      // HTTP 传输特定配置
      "host": "服务器地址",
      "port": 8080
    }
  ]
}
```

### 配置示例

#### 1. Stdio 型 MCP 服务（本地）

```json
{
  "mcpServers": [
    {
      "name": "local-mcp",
      "transport": "stdio",
      "command": "bash ./start-mcp-server.sh"
    }
  ]
}
```

#### 2. HTTP 型 MCP 服务（本地）

```json
{
  "mcpServers": [
    {
      "name": "http-mcp",
      "transport": "http",
      "host": "localhost",
      "port": 8080
    }
  ]
}
```

#### 3. 混合配置（多个服务）

```json
{
  "mcpServers": [
    {
      "name": "local-stdio",
      "transport": "stdio",
      "command": "bash ./local-mcp-server.sh"
    },
    {
      "name": "remote-http",
      "transport": "http",
      "host": "mcp.example.com",
      "port": 9000
    },
    {
      "name": "localhost-http",
      "transport": "http",
      "host": "127.0.0.1",
      "port": 8080
    }
  ]
}
```

## 使用方式

### 1. 配置 MCP 服务

在项目根目录创建或编辑 `mcp-config.json`：

```bash
cat > mcp-config.json << 'EOF'
{
  "mcpServers": [
    {
      "name": "my-tools",
      "transport": "http",
      "host": "localhost",
      "port": 8080
    }
  ]
}
EOF
```

### 2. 启动 LLMOrchestrator

```java
ProviderFactory providerFactory = new ProviderFactory();
RoleFactory roleFactory = new RoleFactory();
LLMOrchestrator orchestrator = new LLMOrchestrator(providerFactory, roleFactory);

// 自动加载 MCP 配置（从 mcp-config.json）
// MCPClientManager 会在初始化时自动尝试加载配置文件
```

### 3. AI 自动使用 MCP 工具

当 AI 需要调用工具时：

```java
// AI 识别需要调用的工具
ToolCall toolCall = new ToolCall("远程-工具-名", {"param": "value"});

// orchestrator 自动识别并执行
String result = orchestrator.handleToolCalls(List.of(toolCall));

// 如果 "远程-工具-名" 来自 MCP 服务，会自动通过 MCPClient 调用
// 否则执行本地工具
```

## 工具冲突处理

如果多个 MCP 服务提供相同名称的工具，系统会：

1. **记录警告**：打印哪个服务发现了冲突
2. **优先级**：使用配置文件中先定义的服务的工具
3. **查找所有者**：可通过 `MCPClientManager.findToolOwner()` 查询工具属于哪个服务

示例日志：
```
⚠️ 工具冲突: read_file (来自 service-a 和 service-b)
📞 通过 service-a 调用 MCP 工具: read_file
```

## 支持的传输方式

### Stdio 传输

**特点**：
- 适合本地服务
- 直接通过标准输入/输出与服务通信
- 命令可以是任何可执行程序

**配置示例**：
```json
{
  "name": "local-service",
  "transport": "stdio",
  "command": "python ./mcp-server.py"
}
```

### HTTP 传输

**特点**：
- 适合网络部署
- 支持远程服务器
- 自动健康检查

**配置示例**：
```json
{
  "name": "remote-service",
  "transport": "http",
  "host": "192.168.1.100",
  "port": 8080
}
```

## API 参考

### LLMOrchestrator

```java
// 获取所有工具定义（包括本地和 MCP 工具）
List<ToolDefinition> allTools = orchestrator.getToolDefinitions();

// 获取 MCP 客户端管理器
MCPClientManager mcpManager = orchestrator.getMCPClientManager();
```

### MCPClientManager

```java
// 检查工具是否来自 MCP
boolean isMcp = mcpManager.isMcpTool("工具名");

// 调用 MCP 工具
String result = mcpManager.callMcpTool("工具名", Map.of("param", "value"));

// 获取所有 MCP 工具定义
Map<String, ToolDefinition> mcpTools = mcpManager.getAllMcpTools();

// 获取统计信息
System.out.println(mcpManager.getStatistics());

// 获取特定服务的客户端
MCPClient client = mcpManager.getClient("service-name");

// 断开所有连接
mcpManager.disconnectAll();
```

## 调试技巧

### 1. 查看MCP初始化日志

```bash
# 启用 DEBUG 日志级别
export LOGGING_LEVEL=DEBUG
java ...
```

日志示例：
```
✅ MCP 客户端已初始化
MCP 客户端统计:
  - 已连接客户端: 2
  - 总工具数: 15
  - local-stdio: 8 个工具
  - remote-http: 7 个工具
```

### 2. 检查工具是否成功加载

```java
MCPClientManager mcpManager = orchestrator.getMCPClientManager();
System.out.println(mcpManager.getStatistics());
```

### 3. 测试单个工具调用

```java
try {
    String result = mcpManager.callMcpTool("read_file",
        Map.of("path", "/tmp/test.txt"));
    System.out.println("成功: " + result);
} catch (Exception e) {
    System.out.println("失败: " + e.getMessage());
}
```

## 常见问题

### Q1: 配置文件不存在时会怎样？

**A**: 系统会记录日志但继续运行，仅使用本地工具。

```
ℹ️ MCP 配置文件不存在: mcp-config.json (将仅使用本地工具)
```

### Q2: 连接 MCP 服务失败时会怎样？

**A**: 系统记录警告但继续运行。该服务的工具不可用。

```
⚠️ 初始化 MCP 客户端失败: 无法连接到 HTTP MCP 服务 (将仅使用本地工具)
```

### Q3: 如何同时使用本地工具和 MCP 工具？

**A**: 完全自动！系统会自动识别工具来源：

```java
List<ToolDefinition> allTools = orchestrator.getToolDefinitions();
// allTools 包含所有本地工具和 MCP 工具混合

orchestrator.handleToolCalls(toolCalls);
// 自动选择本地执行或远程执行
```

### Q4: 工具调用超时怎么办？

**A**: 各传输方式有不同的超时设置：

- **HTTP**: 连接超时 5 秒，读取超时 30 秒
- **Stdio**: 无超时（取决于远程服务）

### Q5: 如何断开所有 MCP 连接？

**A**:
```java
MCPClientManager mcpManager = orchestrator.getMCPClientManager();
mcpManager.disconnectAll();
```

## 性能优化建议

1. **并发调用**：
   - 本地工具和 MCP 工具可以并发调用（不同线程）
   - 同一 MCP 服务的多个工具调用会串行化（单一连接）

2. **连接复用**：
   - MCPClient 会保持与远程服务的连接
   - 避免频繁创建/销毁连接

3. **超时配置**：
   - HTTP 传输默认超时合理，适合大多数场景
   - Stdio 传输没有超时限制，取决于远程服务实现

## 集成示例

### 完整示例

```java
// 1. 初始化 Orchestrator（自动加载 MCP 配置）
ProviderFactory providerFactory = new ProviderFactory();
RoleFactory roleFactory = new RoleFactory();
LLMOrchestrator orchestrator = new LLMOrchestrator(providerFactory, roleFactory);

// 2. 配置 AI 角色
orchestrator.configureRole("analyzer", "openai", "gpt-4");

// 3. 执行 AI 任务
TodoList todoList = orchestrator.analyzeRequirement(
    "分析代码并生成改进建议");

// 4. 生成代码（可能会调用本地和 MCP 工具）
String code = orchestrator.generateCode(context, task);

// 5. 清理
MCPClientManager mcpManager = orchestrator.getMCPClientManager();
mcpManager.disconnectAll();
```

## 故障排除

### 问题1：无法连接到 HTTP MCP 服务

**症状**：
```
❌ MCP 客户端连接失败: remote-service
⚠️ 初始化 MCP 客户端失败: 无法连接到 HTTP MCP 服务
```

**解决**：
1. 检查远程服务是否运行
2. 检查网络连接和防火墙
3. 验证配置中的 host 和 port 正确

### 问题2：Stdio 服务无响应

**症状**：
```
❌ Stdio 连接已关闭
```

**解决**：
1. 检查命令是否正确
2. 确保服务进程正常启动
3. 检查服务日志输出

### 问题3：工具不被识别

**症状**：
```
❌ 工具不是 MCP 工具: tool-name
```

**解决**：
1. 检查 MCP 配置文件是否正确加载
2. 验证工具确实由 MCP 服务提供
3. 查看 MCP 初始化日志中的工具列表

## 相关文件

- `src/main/java/com/harmony/agent/mcp/MCPClient.java` - MCP 客户端实现
- `src/main/java/com/harmony/agent/mcp/MCPClientManager.java` - 客户端管理器
- `src/main/java/com/harmony/agent/mcp/MCPRequest.java` - JSON-RPC 请求
- `src/main/java/com/harmony/agent/mcp/MCPResponse.java` - JSON-RPC 响应
- `mcp-config.json` - 配置文件示例
- `src/main/java/com/harmony/agent/llm/orchestrator/LLMOrchestrator.java` - 已集成 MCP 支持

## 下一步

- [ ] 实现 WebSocket 传输支持
- [ ] 添加工具调用缓存机制
- [ ] 实现断路器模式（服务故障自动降级）
- [ ] 添加工具执行超时配置
- [ ] 支持工具优先级定义
