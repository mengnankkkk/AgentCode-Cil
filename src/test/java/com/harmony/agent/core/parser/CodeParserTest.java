package com.harmony.agent.core.parser;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeParser单元测试
 */
class CodeParserTest {

    private CodeParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new CodeParser();
    }

    /**
     * 测试读取完整文件内容
     */
    @Test
    @DisplayName("read() - 应该成功读取完整文件内容")
    void testRead() throws IOException {
        // Given: 创建测试文件
        Path testFile = tempDir.resolve("test.cpp");
        String content = "int main() {\n    return 0;\n}\n";
        Files.writeString(testFile, content);

        // When: 读取文件
        String result = parser.read(testFile);

        // Then: 内容应该匹配
        assertEquals(content, result);
    }

    /**
     * 测试读取不存在的文件
     */
    @Test
    @DisplayName("read() - 读取不存在的文件应该抛出IOException")
    void testReadNonExistentFile() {
        // Given: 不存在的文件路径
        Path nonExistent = tempDir.resolve("nonexistent.cpp");

        // When & Then: 应该抛出IOException
        assertThrows(IOException.class, () -> parser.read(nonExistent));
    }

    /**
     * 测试读取空文件
     */
    @Test
    @DisplayName("read() - 应该成功读取空文件")
    void testReadEmptyFile() throws IOException {
        // Given: 空文件
        Path emptyFile = tempDir.resolve("empty.cpp");
        Files.writeString(emptyFile, "");

        // When: 读取文件
        String result = parser.read(emptyFile);

        // Then: 应该返回空字符串
        assertEquals("", result);
    }

    /**
     * 测试读取所有行
     */
    @Test
    @DisplayName("readLines() - 应该成功读取所有行")
    void testReadLines() throws IOException {
        // Given: 多行文件
        Path testFile = tempDir.resolve("multi.cpp");
        Files.writeString(testFile, "line1\nline2\nline3\n");

        // When: 读取所有行
        List<String> lines = parser.readLines(testFile);

        // Then: 应该有3行
        assertEquals(3, lines.size());
        assertEquals("line1", lines.get(0));
        assertEquals("line2", lines.get(1));
        assertEquals("line3", lines.get(2));
    }

    /**
     * 测试读取指定行范围
     */
    @Test
    @DisplayName("readLines(start, end) - 应该成功读取指定范围")
    void testReadLinesRange() throws IOException {
        // Given: 10行文件
        Path testFile = tempDir.resolve("range.cpp");
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            content.append("Line ").append(i).append("\n");
        }
        Files.writeString(testFile, content.toString());

        // When: 读取第3-7行
        List<String> lines = parser.readLines(testFile, 3, 7);

        // Then: 应该返回5行
        assertEquals(5, lines.size());
        assertEquals("Line 3", lines.get(0));
        assertEquals("Line 7", lines.get(4));
    }

    /**
     * 测试读取范围超出文件长度
     */
    @Test
    @DisplayName("readLines(start, end) - end超出时应该读取到文件末尾")
    void testReadLinesRangeExceedsLength() throws IOException {
        // Given: 5行文件
        Path testFile = tempDir.resolve("short.cpp");
        Files.writeString(testFile, "1\n2\n3\n4\n5\n");

        // When: 读取1-100行（超出）
        List<String> lines = parser.readLines(testFile, 1, 100);

        // Then: 应该只返回5行
        assertEquals(5, lines.size());
    }

    /**
     * 测试无效的行号范围
     */
    @Test
    @DisplayName("readLines(start, end) - 无效行号应该抛出异常")
    void testReadLinesInvalidRange() throws IOException {
        // Given: 测试文件
        Path testFile = tempDir.resolve("test.cpp");
        Files.writeString(testFile, "line1\nline2\n");

        // When & Then: start < 1 应该抛出异常
        assertThrows(IllegalArgumentException.class,
            () -> parser.readLines(testFile, 0, 5));

        // When & Then: end < start 应该抛出异常
        assertThrows(IllegalArgumentException.class,
            () -> parser.readLines(testFile, 5, 2));

        // When & Then: start超出文件长度
        assertThrows(IllegalArgumentException.class,
            () -> parser.readLines(testFile, 10, 20));
    }

    /**
     * 测试流式读取
     */
    @Test
    @DisplayName("readLinesStream() - 应该成功流式读取指定范围")
    void testReadLinesStream() throws IOException {
        // Given: 大文件
        Path testFile = tempDir.resolve("large.cpp");
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 1000; i++) {
            content.append("Line ").append(i).append("\n");
        }
        Files.writeString(testFile, content.toString());

        // When: 流式读取第500-510行
        List<String> lines = parser.readLinesStream(testFile, 500, 510);

        // Then: 应该返回11行
        assertEquals(11, lines.size());
        assertEquals("Line 500", lines.get(0));
        assertEquals("Line 510", lines.get(10));
    }

    /**
     * 测试检测编码
     */
    @Test
    @DisplayName("detectEncoding() - 应该返回UTF-8")
    void testDetectEncoding() throws IOException {
        // Given: 测试文件
        Path testFile = tempDir.resolve("test.cpp");
        Files.writeString(testFile, "test content");

        // When: 检测编码
        String encoding = parser.detectEncoding(testFile);

        // Then: 应该返回UTF-8
        assertEquals("UTF-8", encoding);
    }

    /**
     * 测试获取文件元数据
     */
    @Test
    @DisplayName("getMetadata() - 应该返回正确的文件元数据")
    void testGetMetadata() throws IOException {
        // Given: 测试文件
        Path testFile = tempDir.resolve("meta.cpp");
        String content = "line1\nline2\nline3\n";
        Files.writeString(testFile, content);

        // When: 获取元数据
        CodeParser.FileMetadata metadata = parser.getMetadata(testFile);

        // Then: 验证元数据
        assertNotNull(metadata);
        assertEquals(testFile, metadata.getPath());
        assertEquals(3, metadata.getLineCount());
        assertEquals("UTF-8", metadata.getEncoding());
        assertTrue(metadata.getSizeBytes() > 0);
        assertEquals("meta.cpp", metadata.getFileName());
        assertEquals(".cpp", metadata.getExtension());
    }

    /**
     * 测试FileMetadata的人类可读大小
     */
    @Test
    @DisplayName("FileMetadata.getHumanReadableSize() - 应该返回人类可读的大小")
    void testFileMetadataHumanReadableSize() throws IOException {
        // Given: 不同大小的文件
        Path smallFile = tempDir.resolve("small.cpp");
        Files.writeString(smallFile, "x");  // 1 byte

        Path mediumFile = tempDir.resolve("medium.cpp");
        Files.writeString(mediumFile, "x".repeat(2048));  // 2 KB

        // When: 获取元数据
        CodeParser.FileMetadata smallMeta = parser.getMetadata(smallFile);
        CodeParser.FileMetadata mediumMeta = parser.getMetadata(mediumFile);

        // Then: 验证大小格式
        assertTrue(smallMeta.getHumanReadableSize().contains("B"));
        assertTrue(mediumMeta.getHumanReadableSize().contains("KB"));
    }

    /**
     * 测试null文件路径
     */
    @Test
    @DisplayName("read(null) - null路径应该抛出异常")
    void testReadNullPath() {
        // When & Then: 应该抛出IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> parser.read(null));
    }

    /**
     * 测试读取目录
     */
    @Test
    @DisplayName("read(directory) - 读取目录应该抛出IOException")
    void testReadDirectory() {
        // Given: 目录路径
        Path directory = tempDir;

        // When & Then: 应该抛出IOException
        IOException exception = assertThrows(IOException.class,
            () -> parser.read(directory));
        assertTrue(exception.getMessage().contains("Not a regular file"));
    }

    /**
     * 测试UTF-8编码的中文内容
     */
    @Test
    @DisplayName("read() - 应该正确读取UTF-8中文内容")
    void testReadChineseContent() throws IOException {
        // Given: 包含中文的文件
        Path testFile = tempDir.resolve("chinese.cpp");
        String content = "// 这是中文注释\nint 变量 = 100;\n";
        Files.writeString(testFile, content);

        // When: 读取文件
        String result = parser.read(testFile);

        // Then: 中文应该正确显示
        assertEquals(content, result);
        assertTrue(result.contains("中文"));
    }

    /**
     * 测试toString方法
     */
    @Test
    @DisplayName("FileMetadata.toString() - 应该返回格式化的字符串")
    void testFileMetadataToString() throws IOException {
        // Given: 测试文件
        Path testFile = tempDir.resolve("test.cpp");
        Files.writeString(testFile, "test\n");

        // When: 获取元数据并转字符串
        CodeParser.FileMetadata metadata = parser.getMetadata(testFile);
        String str = metadata.toString();

        // Then: 应该包含关键信息
        assertTrue(str.contains("FileMetadata"));
        assertTrue(str.contains("test.cpp"));
        assertTrue(str.contains("bytes"));
        assertTrue(str.contains("lines"));
        assertTrue(str.contains("UTF-8"));
    }
}
