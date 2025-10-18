package com.harmony.agent.core.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 代码解析器基础类
 * 按需读取文件内容，不扫描文件系统
 *
 * 使用场景：
 * - 读取完整文件内容
 * - 按行读取
 * - 读取指定行范围（用于显示上下文）
 * - 获取文件元数据
 */
public class CodeParser {

    private static final Logger logger = LoggerFactory.getLogger(CodeParser.class);
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * 读取文件全部内容
     *
     * @param file 文件路径
     * @return 文件内容字符串
     * @throws IOException 文件不存在或读取失败
     */
    public String read(Path file) throws IOException {
        validateFile(file);

        logger.debug("Reading file: {}", file);
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes, DEFAULT_CHARSET);
    }

    /**
     * 读取文件所有行
     *
     * @param file 文件路径
     * @return 行列表
     * @throws IOException 文件不存在或读取失败
     */
    public List<String> readLines(Path file) throws IOException {
        validateFile(file);

        logger.debug("Reading lines from file: {}", file);
        return Files.readAllLines(file, DEFAULT_CHARSET);
    }

    /**
     * 读取指定行范围
     *
     * @param file 文件路径
     * @param start 起始行号（1-based，包含）
     * @param end 结束行号（1-based，包含）
     * @return 指定范围的行列表
     * @throws IOException 文件不存在或读取失败
     * @throws IllegalArgumentException 行号无效
     */
    public List<String> readLines(Path file, int start, int end) throws IOException {
        if (start < 1) {
            throw new IllegalArgumentException("Start line must be >= 1, got: " + start);
        }

        if (end < start) {
            throw new IllegalArgumentException(
                String.format("End line (%d) must be >= start line (%d)", end, start)
            );
        }

        List<String> allLines = readLines(file);

        if (start > allLines.size()) {
            throw new IllegalArgumentException(
                String.format("Start line (%d) exceeds file length (%d)", start, allLines.size())
            );
        }

        // 调整end到实际文件行数
        int actualEnd = Math.min(end, allLines.size());

        logger.debug("Reading lines {}-{} from file: {}", start, actualEnd, file);

        // Convert to 0-based index: [start-1, actualEnd)
        return allLines.subList(start - 1, actualEnd);
    }

    /**
     * 使用流式读取指定行范围（适用于大文件）
     *
     * @param file 文件路径
     * @param start 起始行号（1-based，包含）
     * @param end 结束行号（1-based，包含）
     * @return 指定范围的行列表
     * @throws IOException 文件不存在或读取失败
     */
    public List<String> readLinesStream(Path file, int start, int end) throws IOException {
        if (start < 1) {
            throw new IllegalArgumentException("Start line must be >= 1, got: " + start);
        }

        if (end < start) {
            throw new IllegalArgumentException(
                String.format("End line (%d) must be >= start line (%d)", end, start)
            );
        }

        validateFile(file);

        logger.debug("Streaming lines {}-{} from file: {}", start, end, file);

        try (Stream<String> lines = Files.lines(file, DEFAULT_CHARSET)) {
            return lines
                .skip(start - 1)  // Skip to start line
                .limit(end - start + 1)  // Limit to range
                .collect(Collectors.toList());
        }
    }

    /**
     * 检测文件编码
     * 当前简化版：总是返回UTF-8
     * 后续可增强：使用juniversalchardet库检测
     *
     * @param file 文件路径
     * @return 编码名称
     * @throws IOException 文件不存在
     */
    public String detectEncoding(Path file) throws IOException {
        validateFile(file);

        // 简化版：总是返回UTF-8
        // TODO: 阶段3增强 - 实现真实编码检测
        return DEFAULT_CHARSET.name();
    }

    /**
     * 获取文件元数据
     *
     * @param file 文件路径
     * @return 文件元数据对象
     * @throws IOException 文件不存在或读取失败
     */
    public FileMetadata getMetadata(Path file) throws IOException {
        validateFile(file);

        logger.debug("Getting metadata for file: {}", file);

        long sizeBytes = Files.size(file);
        long lineCount = countLines(file);
        String encoding = detectEncoding(file);

        return new FileMetadata(file, sizeBytes, lineCount, encoding);
    }

    /**
     * 统计文件行数
     *
     * @param file 文件路径
     * @return 行数
     * @throws IOException 读取失败
     */
    private long countLines(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file, DEFAULT_CHARSET)) {
            return lines.count();
        }
    }

    /**
     * 验证文件是否存在且可读
     *
     * @param file 文件路径
     * @throws IOException 文件不存在或不可读
     */
    private void validateFile(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }

        if (!Files.exists(file)) {
            throw new IOException("File not found: " + file);
        }

        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a regular file: " + file);
        }

        if (!Files.isReadable(file)) {
            throw new IOException("File is not readable: " + file);
        }
    }

    /**
     * 文件元数据
     */
    public static class FileMetadata {
        private final Path path;
        private final long sizeBytes;
        private final long lineCount;
        private final String encoding;

        public FileMetadata(Path path, long sizeBytes, long lineCount, String encoding) {
            this.path = path;
            this.sizeBytes = sizeBytes;
            this.lineCount = lineCount;
            this.encoding = encoding;
        }

        /**
         * 获取文件路径
         */
        public Path getPath() {
            return path;
        }

        /**
         * 获取文件大小（字节）
         */
        public long getSizeBytes() {
            return sizeBytes;
        }

        /**
         * 获取文件大小（KB）
         */
        public double getSizeKB() {
            return sizeBytes / 1024.0;
        }

        /**
         * 获取文件大小（MB）
         */
        public double getSizeMB() {
            return sizeBytes / (1024.0 * 1024.0);
        }

        /**
         * 获取行数
         */
        public long getLineCount() {
            return lineCount;
        }

        /**
         * 获取编码
         */
        public String getEncoding() {
            return encoding;
        }

        /**
         * 获取文件名
         */
        public String getFileName() {
            return path.getFileName().toString();
        }

        /**
         * 获取文件扩展名
         */
        public String getExtension() {
            String fileName = getFileName();
            int dotIndex = fileName.lastIndexOf('.');
            return dotIndex > 0 ? fileName.substring(dotIndex) : "";
        }

        @Override
        public String toString() {
            return String.format(
                "FileMetadata[path=%s, size=%d bytes (%.2f KB), lines=%d, encoding=%s]",
                path, sizeBytes, getSizeKB(), lineCount, encoding
            );
        }

        /**
         * 获取人类可读的大小描述
         */
        public String getHumanReadableSize() {
            if (sizeBytes < 1024) {
                return sizeBytes + " B";
            } else if (sizeBytes < 1024 * 1024) {
                return String.format("%.2f KB", getSizeKB());
            } else {
                return String.format("%.2f MB", getSizeMB());
            }
        }
    }
}
