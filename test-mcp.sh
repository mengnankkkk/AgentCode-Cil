#!/bin/bash
# MCP 服务器功能测试脚本

set -e

echo "🧪 MCP 服务器功能测试"
echo "===================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 curl 是否安装
if ! command -v curl &> /dev/null; then
    echo -e "${RED}❌ 需要安装 curl${NC}"
    exit 1
fi

# 检查 jq 是否安装
if ! command -v jq &> /dev/null; then
    echo -e "${RED}❌ 需要安装 jq (JSON 查询工具)${NC}"
    exit 1
fi

# 编译项目
echo -e "${YELLOW}📦 编译项目...${NC}"
if mvn clean compile -q > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 编译成功${NC}"
else
    echo -e "${RED}❌ 编译失败${NC}"
    exit 1
fi

# 启动 HTTP 服务器（后台运行）
echo -e "${YELLOW}🚀 启动 MCP HTTP 服务器 (端口 8090)...${NC}"
mvn exec:java -Dexec.mainClass="com.harmony.agent.mcp.MCPServerLauncher" \
              -Dexec.args="--transport=http --host=localhost --port=8090" \
              -q > /dev/null 2>&1 &
MCP_PID=$!

# 等待服务器启动
sleep 3

# 检查服务器是否启动成功
if ! kill -0 $MCP_PID 2>/dev/null; then
    echo -e "${RED}❌ 服务器启动失败${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 服务器已启动 (PID: $MCP_PID)${NC}"
echo ""

# 清理函数
cleanup() {
    echo -e "${YELLOW}🛑 关闭测试服务器...${NC}"
    kill $MCP_PID 2>/dev/null || true
    wait $MCP_PID 2>/dev/null || true
    echo -e "${GREEN}✅ 已关闭${NC}"
}

# 设置退出陷阱
trap cleanup EXIT

# 测试 1: 健康检查
echo -e "${YELLOW}测试 1: 健康检查 (/health)${NC}"
RESPONSE=$(curl -s http://localhost:8090/health)
if echo "$RESPONSE" | jq -e '.status == "healthy"' > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过${NC}"
else
    echo -e "${RED}❌ 失败${NC}"
    echo "响应: $RESPONSE"
fi
echo ""

# 测试 2: 初始化
echo -e "${YELLOW}测试 2: 初始化 (initialize)${NC}"
RESPONSE=$(curl -s -X POST http://localhost:8090/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "initialize",
    "id": "test-1"
  }')

if echo "$RESPONSE" | jq -e '.result.serverInfo.name == "harmony-safe-agent-mcp"' > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过${NC}"
    echo "服务器: $(echo "$RESPONSE" | jq -r '.result.serverInfo | "\(.name) v\(.version)"')"
else
    echo -e "${RED}❌ 失败${NC}"
    echo "响应: $RESPONSE"
fi
echo ""

# 测试 3: 列出工具
echo -e "${YELLOW}测试 3: 列出工具 (tools/list)${NC}"
RESPONSE=$(curl -s -X POST http://localhost:8090/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": "test-2"
  }')

TOOL_COUNT=$(echo "$RESPONSE" | jq -r '.result.tools | length')
if [ "$TOOL_COUNT" -eq 19 ]; then
    echo -e "${GREEN}✅ 通过${NC}"
    echo "工具数量: $TOOL_COUNT"
    echo "工具列表:"
    echo "$RESPONSE" | jq -r '.result.tools[] | "  - \(.name): \(.description)"' | head -10
    echo "  ..."
else
    echo -e "${RED}❌ 失败 (期望 19 个工具，实际 $TOOL_COUNT 个)${NC}"
fi
echo ""

# 测试 4: 调用工具 (pwd)
echo -e "${YELLOW}测试 4: 调用工具 - pwd${NC}"
RESPONSE=$(curl -s -X POST http://localhost:8090/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "pwd",
      "arguments": {}
    },
    "id": "test-3"
  }')

if echo "$RESPONSE" | jq -e '.result.content[0].type == "text"' > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过${NC}"
    PWD_OUTPUT=$(echo "$RESPONSE" | jq -r '.result.content[0].text')
    echo "当前目录: $PWD_OUTPUT"
else
    echo -e "${RED}❌ 失败${NC}"
    echo "响应: $RESPONSE"
fi
echo ""

# 测试 5: 调用工具 (ls)
echo -e "${YELLOW}测试 5: 调用工具 - ls${NC}"
RESPONSE=$(curl -s -X POST http://localhost:8090/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "ls",
      "arguments": {
        "path": "."
      }
    },
    "id": "test-4"
  }')

if echo "$RESPONSE" | jq -e '.result.content[0].type == "text"' > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过${NC}"
    echo "目录内容 (前 5 行):"
    echo "$RESPONSE" | jq -r '.result.content[0].text' | head -5
else
    echo -e "${RED}❌ 失败${NC}"
    echo "响应: $RESPONSE"
fi
echo ""

# 测试 6: 列出资源
echo -e "${YELLOW}测试 6: 列出资源 (resources/list)${NC}"
RESPONSE=$(curl -s -X POST http://localhost:8090/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "resources/list",
    "id": "test-5"
  }')

if echo "$RESPONSE" | jq -e '.result.resources | length > 0' > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过${NC}"
    echo "$RESPONSE" | jq -r '.result.resources[] | "  - \(.uri): \(.description)"'
else
    echo -e "${RED}❌ 失败${NC}"
    echo "响应: $RESPONSE"
fi
echo ""

# 测试 7: 无效请求
echo -e "${YELLOW}测试 7: 错误处理 (无效工具名)${NC}"
RESPONSE=$(curl -s -X POST http://localhost:8090/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "invalid_tool",
      "arguments": {}
    },
    "id": "test-6"
  }')

if echo "$RESPONSE" | jq -e '.error.code == -32602' > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过 (返回错误代码 -32602)${NC}"
    echo "错误消息: $(echo "$RESPONSE" | jq -r '.error.message')"
else
    echo -e "${RED}❌ 失败${NC}"
    echo "响应: $RESPONSE"
fi
echo ""

echo "🎉 所有测试完成！"
