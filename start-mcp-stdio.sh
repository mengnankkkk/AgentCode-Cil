#!/bin/bash
# MCP 服务器启动脚本（Linux/macOS）

# 编译项目
echo "📦 编译项目..."
mvn clean compile > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"

# 启动 Stdio 模式（用于与 Claude API 集成）
echo ""
echo "🚀 启动 MCP Stdio 服务器..."
echo "用法说明："
echo "  - 从标准输入读取 JSON-RPC 2.0 请求"
echo "  - 每行一个 JSON 对象"
echo "  - 例如: {\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":\"1\"}"
echo ""

# 运行 Java 程序
mvn exec:java -Dexec.mainClass="com.harmony.agent.mcp.MCPServerLauncher" \
              -Dexec.args="--transport=stdio" \
              -q
