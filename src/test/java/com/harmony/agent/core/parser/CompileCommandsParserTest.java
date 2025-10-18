package com.harmony.agent.core.parser;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompileCommandsParser单元测试
 */
class CompileCommandsParserTest {

    @TempDir
    Path tempDir;

    /**
     * 测试解析标准compile_commands.json
     */
    @Test
    @DisplayName("parse() - 应该成功解析标准compile_commands.json")
    void testParseStandardFormat() throws IOException {
        // Given: 标准格式的compile_commands.json
        String json = """
            [
              {
                "directory": "/home/user/project/build",
                "command": "gcc -Iinclude -DDEBUG -std=c++17 main.cpp -o main.o",
                "file": "main.cpp"
              },
              {
                "directory": "/home/user/project/build",
                "arguments": ["g++", "-Isrc", "-DVERSION=1.0", "test.cpp", "-o", "test.o"],
                "file": "test.cpp"
              }
            ]
            """;

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);

        // When: 解析文件
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // Then: 应该解析出2个命令
        List<CompileCommandsParser.CompileCommand> commands = parser.getCommands();
        assertEquals(2, commands.size());

        assertEquals("main.cpp", commands.get(0).file);
        assertEquals("test.cpp", commands.get(1).file);
    }

    /**
     * 测试获取所有源文件
     */
    @Test
    @DisplayName("getSourceFiles() - 应该提取所有源文件路径")
    void testGetSourceFiles() throws IOException {
        // Given: compile_commands.json with 3 files
        String json = """
            [
              {
                "directory": "/project",
                "command": "gcc main.c -o main.o",
                "file": "src/main.c"
              },
              {
                "directory": "/project",
                "command": "gcc utils.c -o utils.o",
                "file": "src/utils.c"
              },
              {
                "directory": "/project",
                "command": "gcc test.c -o test.o",
                "file": "test/test.c"
              }
            ]
            """;

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // When: 获取源文件
        Set<Path> sourceFiles = parser.getSourceFiles();

        // Then: 应该返回3个文件（去重后）
        assertEquals(3, sourceFiles.size());
    }

    /**
     * 测试提取包含路径
     */
    @Test
    @DisplayName("getIncludePaths() - 应该提取所有-I选项")
    void testGetIncludePaths() throws IOException {
        // Given: 包含多个-I选项的命令
        String json = """
            [
              {
                "directory": "/project",
                "command": "gcc -Iinclude -I /usr/local/include -isystem /usr/include main.c",
                "file": "main.c"
              },
              {
                "directory": "/project",
                "arguments": ["gcc", "-Isrc", "-iquote", "headers", "test.c"],
                "file": "test.c"
              }
            ]
            """;

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // When: 获取包含路径
        List<Path> includePaths = parser.getIncludePaths();

        // Then: 应该提取所有-I/-isystem/-iquote路径
        assertTrue(includePaths.size() >= 4);
    }

    /**
     * 测试提取宏定义
     */
    @Test
    @DisplayName("getDefines() - 应该提取所有-D选项")
    void testGetDefines() throws IOException {
        // Given: 包含-D选项的命令
        String json = """
            [
              {
                "directory": "/project",
                "command": "gcc -DDEBUG -DVERSION=1.0 -DPLATFORM=linux main.c",
                "file": "main.c"
              },
              {
                "directory": "/project",
                "arguments": ["gcc", "-DRELEASE", "-DOPTIMIZE=2", "test.c"],
                "file": "test.c"
              }
            ]
            """;

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // When: 获取宏定义
        Map<String, String> defines = parser.getDefines();

        // Then: 应该解析所有宏定义
        assertTrue(defines.containsKey("DEBUG"));
        assertEquals("", defines.get("DEBUG"));  // 无值的宏

        assertTrue(defines.containsKey("VERSION"));
        assertEquals("1.0", defines.get("VERSION"));  // 有值的宏

        assertTrue(defines.containsKey("PLATFORM"));
        assertEquals("linux", defines.get("PLATFORM"));

        assertTrue(defines.containsKey("RELEASE"));
        assertTrue(defines.containsKey("OPTIMIZE"));
        assertEquals("2", defines.get("OPTIMIZE"));
    }

    /**
     * 测试根据源文件获取编译命令
     */
    @Test
    @DisplayName("getCommandForFile() - 应该根据文件名查找命令")
    void testGetCommandForFile() throws IOException {
        // Given: compile_commands.json
        String json = """
            [
              {
                "directory": "/project",
                "command": "gcc main.c -o main.o",
                "file": "src/main.c"
              },
              {
                "directory": "/project",
                "command": "gcc test.c -o test.o",
                "file": "test/test.c"
              }
            ]
            """;

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // When: 查找main.c的命令
        Path mainFile = Path.of("src/main.c");
        CompileCommandsParser.CompileCommand cmd = parser.getCommandForFile(mainFile);

        // Then: 应该找到对应的命令
        assertNotNull(cmd);
        assertTrue(cmd.file.endsWith("main.c"));
    }

    /**
     * 测试获取统计信息
     */
    @Test
    @DisplayName("getStatistics() - 应该返回正确的统计信息")
    void testGetStatistics() throws IOException {
        // Given: compile_commands.json with known content
        String json = """
            [
              {
                "directory": "/project",
                "command": "gcc -Iinclude -DDEBUG main.c -o main.o",
                "file": "main.c"
              },
              {
                "directory": "/project",
                "command": "gcc -Isrc -DRELEASE test.c -o test.o",
                "file": "test.c"
              }
            ]
            """;

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // When: 获取统计信息
        CompileCommandsParser.CompilationStatistics stats = parser.getStatistics();

        // Then: 验证统计数据
        assertEquals(2, stats.getTotalCommands());
        assertEquals(2, stats.getUniqueFiles());
        assertTrue(stats.getIncludePaths() >= 2);  // include, src
        assertTrue(stats.getDefines() >= 2);  // DEBUG, RELEASE
    }

    /**
     * 测试文件不存在
     */
    @Test
    @DisplayName("parse() - 文件不存在应该抛出IOException")
    void testFileNotFound() {
        // Given: 不存在的文件路径
        Path nonExistent = tempDir.resolve("nonexistent.json");

        // When & Then: 应该抛出IOException
        IOException exception = assertThrows(IOException.class,
            () -> new CompileCommandsParser(nonExistent));
        assertTrue(exception.getMessage().contains("not found"));
    }

    /**
     * 测试空JSON文件
     */
    @Test
    @DisplayName("parse() - 空JSON数组应该返回空列表")
    void testEmptyJson() throws IOException {
        // Given: 空的JSON数组
        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, "[]");

        // When: 解析文件
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // Then: 应该返回空列表
        assertEquals(0, parser.getCommands().size());
        assertEquals(0, parser.getSourceFiles().size());
    }

    /**
     * 测试无效JSON格式
     */
    @Test
    @DisplayName("parse() - 无效JSON应该抛出IOException")
    void testInvalidJson() {
        // Given: 无效的JSON内容
        Path jsonFile = tempDir.resolve("compile_commands.json");

        assertDoesNotThrow(() -> {
            Files.writeString(jsonFile, "{ invalid json content }");
        });

        // When & Then: 应该抛出IOException
        assertThrows(IOException.class,
            () -> new CompileCommandsParser(jsonFile));
    }

    /**
     * 测试arguments字段优先于command字段
     */
    @Test
    @DisplayName("CompileCommand - arguments字段应该优先于command字段")
    void testArgumentsPriority() throws IOException {
        // Given: 同时包含arguments和command的条目
        String json = """
            [
              {
                "directory": "/project",
                "command": "gcc -DOLD main.c",
                "arguments": ["gcc", "-DNEW", "main.c"],
                "file": "main.c"
              }
            ]
            """;

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // When: 获取宏定义
        Map<String, String> defines = parser.getDefines();

        // Then: 应该使用arguments字段的定义
        assertTrue(defines.containsKey("NEW"));
        assertFalse(defines.containsKey("OLD"));
    }

    /**
     * 测试C++标准选项解析
     */
    @Test
    @DisplayName("getStandard() - 应该解析-std选项")
    void testGetStandard() throws IOException {
        // Given: 包含-std选项的命令
        String json = """
            [
              {
                "directory": "/project",
                "command": "g++ -std=c++17 main.cpp -o main.o",
                "file": "main.cpp"
              }
            ]
            """;

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // When: 获取标准选项
        CompileCommandsParser.CompileCommand cmd = parser.getCommands().get(0);
        String standard = cmd.getStandard();

        // Then: 应该解析出c++17
        assertEquals("c++17", standard);
    }

    /**
     * 测试相对路径解析
     */
    @Test
    @DisplayName("resolvePath() - 应该正确解析相对路径")
    void testRelativePathResolution() throws IOException {
        // Given: 使用相对路径的compile_commands.json (使用tempDir作为directory以跨平台兼容)
        String directory = tempDir.resolve("build").toString();
        String json = String.format("""
            [
              {
                "directory": "%s",
                "command": "gcc ../src/main.c -o main.o",
                "file": "../src/main.c"
              }
            ]
            """, directory.replace("\\", "\\\\"));

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // When: 获取源文件路径
        Set<Path> sourceFiles = parser.getSourceFiles();

        // Then: 应该解析为规范化的路径（normalize会处理..）
        assertEquals(1, sourceFiles.size());
        Path sourceFile = sourceFiles.iterator().next();
        // 检查路径被规范化（不包含..）
        assertFalse(sourceFile.toString().contains(".."));
    }

    /**
     * 测试toString方法
     */
    @Test
    @DisplayName("toString() - 应该返回格式化的字符串")
    void testToString() throws IOException {
        // Given: compile_commands.json
        String json = """
            [
              {
                "directory": "/project",
                "command": "gcc main.c -o main.o",
                "file": "main.c"
              }
            ]
            """;

        Path jsonFile = tempDir.resolve("compile_commands.json");
        Files.writeString(jsonFile, json);
        CompileCommandsParser parser = new CompileCommandsParser(jsonFile);

        // When: 获取统计信息并转字符串
        CompileCommandsParser.CompilationStatistics stats = parser.getStatistics();
        String str = stats.toString();

        // Then: 应该包含关键信息
        assertTrue(str.contains("CompilationStatistics"));
        assertTrue(str.contains("commands="));
        assertTrue(str.contains("files="));
    }
}
