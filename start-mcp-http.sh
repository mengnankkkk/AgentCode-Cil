#!/bin/bash
# MCP HTTP 服务器启动脚本（Linux/macOS）

# 编译项目
echo "📦 编译项目..."
mvn clean compile > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"

# 解析参数
PORT=${1:-8080}
HOST=${2:-localhost}

echo ""
echo "🚀 启动 MCP HTTP 服务器..."
echo "配置信息："
echo "  - 监听地址: http://$HOST:$PORT"
echo "  - MCP 端点: /mcp (POST)"
echo "  - 健康检查: /health (GET)"
echo ""
echo "示例请求："
echo "  curl -X POST http://localhost:$PORT/mcp \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":\"1\"}'"
echo ""

# 运行 Java 程序
mvn exec:java -Dexec.mainClass="com.harmony.agent.mcp.MCPServerLauncher" \
              -Dexec.args="--transport=http --host=$HOST --port=$PORT" \
              -q
