@echo off
REM MCP HTTP 服务器启动脚本（Windows）

echo 编译项目...
call mvn clean compile -q > nul 2>&1

if %errorlevel% neq 0 (
    echo 编译失败
    exit /b 1
)

echo 编译成功
echo.

REM 解析参数
set PORT=%1
if "%PORT%"=="" set PORT=8080

set HOST=%2
if "%HOST%"=="" set HOST=localhost

echo 启动 MCP HTTP 服务器...
echo 配置信息：
echo   - 监听地址: http://%HOST%:%PORT%
echo   - MCP 端点: /mcp (POST)
echo   - 健康检查: /health (GET)
echo.
echo 示例请求：
echo   curl -X POST http://localhost:%PORT%/mcp ^
echo     -H "Content-Type: application/json" ^
echo     -d "{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":\"1\"}"
echo.

REM 运行 Java 程序
mvn exec:java -Dexec.mainClass="com.harmony.agent.mcp.MCPServerLauncher" ^
              -Dexec.args="--transport=http --host=%HOST% --port=%PORT%" ^
              -q
