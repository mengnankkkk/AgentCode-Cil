@echo off
REM MCP 服务器功能测试脚本（Windows）

setlocal enabledelayedexpansion

echo 蔧 MCP 服务器功能测试
echo ====================
echo.

REM 检查编译
echo 编译项目...
call mvn clean compile -q > nul 2>&1

if %errorlevel% neq 0 (
    echo 编译失败
    exit /b 1
)

echo 编译成功
echo.

REM 启动 HTTP 服务器（后台运行）
echo 启动 MCP HTTP 服务器 (端口 8090)...

REM 生成临时脚本来启动服务器
(
    @echo off
    mvn exec:java -Dexec.mainClass="com.harmony.agent.mcp.MCPServerLauncher" ^
                  -Dexec.args="--transport=http --host=localhost --port=8090" ^
                  -q
) > temp-mcp-server.bat

REM 启动服务器
start /B temp-mcp-server.bat
set MCP_PID=%ERRORLEVEL%

REM 等待服务器启动
timeout /t 3 /nobreak > nul

echo 服务器已启动
echo.

echo 测试 1: 健康检查 (/health)
curl -s http://localhost:8090/health | jq . || (
    echo 失败
    goto cleanup
)
echo.

echo 测试 2: 初始化 (initialize)
curl -s -X POST http://localhost:8090/mcp ^
  -H "Content-Type: application/json" ^
  -d "{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":\"test-1\"}" | jq . || (
    echo 失败
    goto cleanup
)
echo.

echo 测试 3: 列出工具 (tools/list)
curl -s -X POST http://localhost:8090/mcp ^
  -H "Content-Type: application/json" ^
  -d "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":\"test-2\"}" | jq ".result.tools | length" || (
    echo 失败
    goto cleanup
)
echo.

echo 测试 4: 调用工具 - pwd
curl -s -X POST http://localhost:8090/mcp ^
  -H "Content-Type: application/json" ^
  -d "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"pwd\",\"arguments\":{}},\"id\":\"test-3\"}" | jq . || (
    echo 失败
    goto cleanup
)
echo.

:cleanup
REM 清理
echo.
echo 关闭测试服务器...
taskkill /F /FI "WINDOWTITLE eq temp-mcp-server.bat" > nul 2>&1
del temp-mcp-server.bat > nul 2>&1

echo 测试完成！
