@echo off
REM MCP 服务器启动脚本（Windows）

echo 编译项目...
call mvn clean compile -q > nul 2>&1

if %errorlevel% neq 0 (
    echo 编译失败
    exit /b 1
)

echo 编译成功
echo.
echo 启动 MCP Stdio 服务器...
echo 用法说明：
echo   - 从标准输入读取 JSON-RPC 2.0 请求
echo   - 每行一个 JSON 对象
echo   - 例如: {"jsonrpc":"2.0","method":"initialize","id":"1"}
echo.

REM 运行 Java 程序
mvn exec:java -Dexec.mainClass="com.harmony.agent.mcp.MCPServerLauncher" ^
              -Dexec.args="--transport=stdio" ^
              -q
