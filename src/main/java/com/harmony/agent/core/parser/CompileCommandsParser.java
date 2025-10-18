package com.harmony.agent.core.parser;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * compile_commands.json 解析器
 *
 * 解析LLVM格式的编译数据库(Compilation Database),提取:
 * - 源文件列表
 * - 包含路径(-I)
 * - 宏定义(-D)
 * - 编译选项
 *
 * 支持的构建系统:
 * - CMake: cmake -DCMAKE_EXPORT_COMPILE_COMMANDS=ON ..
 * - Bear: bear -- make
 * - Ninja: ninja -t compdb > compile_commands.json
 *
 * compile_commands.json格式:
 * [
 *   {
 *     "directory": "/path/to/build",
 *     "command": "gcc -Iinclude -DDEBUG main.c -o main.o",
 *     "file": "main.c",
 *     "arguments": ["gcc", "-Iinclude", "-DDEBUG", "main.c", "-o", "main.o"]
 *   },
 *   ...
 * ]
 */
public class CompileCommandsParser {

    private static final Logger logger = LoggerFactory.getLogger(CompileCommandsParser.class);

    private final Path jsonPath;
    private final List<CompileCommand> commands;
    private final Gson gson;

    /**
     * 构造函数
     *
     * @param jsonPath compile_commands.json文件路径
     * @throws IOException 文件不存在或解析失败
     */
    public CompileCommandsParser(Path jsonPath) throws IOException {
        this.jsonPath = jsonPath;
        this.gson = new Gson();
        this.commands = parseFile();

        logger.info("Loaded {} compile commands from: {}", commands.size(), jsonPath);
    }

    /**
     * 解析JSON文件
     */
    private List<CompileCommand> parseFile() throws IOException {
        if (!Files.exists(jsonPath)) {
            throw new IOException("compile_commands.json not found: " + jsonPath);
        }

        if (!Files.isRegularFile(jsonPath)) {
            throw new IOException("Not a regular file: " + jsonPath);
        }

        try {
            String jsonContent = Files.readString(jsonPath);
            CompileCommand[] commandArray = gson.fromJson(jsonContent, CompileCommand[].class);

            if (commandArray == null || commandArray.length == 0) {
                logger.warn("Empty compile_commands.json file");
                return Collections.emptyList();
            }

            return Arrays.asList(commandArray);

        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid JSON format in compile_commands.json: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有编译命令
     */
    public List<CompileCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    /**
     * 获取所有源文件路径
     *
     * @return 源文件路径集合(已解析为绝对路径)
     */
    public Set<Path> getSourceFiles() {
        return commands.stream()
            .map(cmd -> resolvePath(cmd.directory, cmd.file))
            .collect(Collectors.toSet());
    }

    /**
     * 获取所有包含路径
     *
     * @return 包含路径列表(已解析为绝对路径)
     */
    public List<Path> getIncludePaths() {
        return commands.stream()
            .flatMap(cmd -> cmd.getIncludePaths().stream()
                .map(include -> resolvePath(cmd.directory, include)))
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 获取所有宏定义
     *
     * @return 宏定义映射 (名称 -> 值)
     */
    public Map<String, String> getDefines() {
        Map<String, String> allDefines = new HashMap<>();

        for (CompileCommand cmd : commands) {
            allDefines.putAll(cmd.getDefines());
        }

        return allDefines;
    }

    /**
     * 根据源文件获取编译命令
     *
     * @param sourceFile 源文件路径
     * @return 编译命令，不存在则返回null
     */
    public CompileCommand getCommandForFile(Path sourceFile) {
        String fileName = sourceFile.getFileName().toString();

        return commands.stream()
            .filter(cmd -> cmd.file != null && cmd.file.endsWith(fileName))
            .findFirst()
            .orElse(null);
    }

    /**
     * 解析路径(相对->绝对)
     *
     * @param directory 工作目录
     * @param path 文件路径
     * @return 绝对路径
     */
    private Path resolvePath(String directory, String path) {
        Path filePath = Paths.get(path);

        if (filePath.isAbsolute()) {
            return filePath.normalize();
        }

        // 相对路径,相对于directory
        Path dirPath = Paths.get(directory);
        return dirPath.resolve(filePath).normalize();
    }

    /**
     * 获取统计信息
     */
    public CompilationStatistics getStatistics() {
        int totalCommands = commands.size();
        int uniqueFiles = getSourceFiles().size();
        int includePaths = getIncludePaths().size();
        int defines = getDefines().size();

        return new CompilationStatistics(totalCommands, uniqueFiles, includePaths, defines);
    }

    /**
     * 编译命令
     */
    public static class CompileCommand {
        /** 工作目录 */
        public String directory;

        /** 完整编译命令字符串 */
        public String command;

        /** 源文件路径 */
        public String file;

        /** 编译参数数组(优先使用) */
        public String[] arguments;

        /** 输出文件(可选) */
        public String output;

        /**
         * 提取包含路径(-I选项)
         */
        public List<String> getIncludePaths() {
            List<String> includes = new ArrayList<>();

            List<String> args = getArgumentsList();
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);

                if (arg.startsWith("-I")) {
                    // -Iinclude 或 -I include
                    if (arg.length() > 2) {
                        includes.add(arg.substring(2));
                    } else if (i + 1 < args.size()) {
                        includes.add(args.get(i + 1));
                        i++;
                    }
                } else if (arg.equals("-isystem") || arg.equals("-iquote")) {
                    // -isystem /usr/include
                    if (i + 1 < args.size()) {
                        includes.add(args.get(i + 1));
                        i++;
                    }
                }
            }

            return includes;
        }

        /**
         * 提取宏定义(-D选项)
         */
        public Map<String, String> getDefines() {
            Map<String, String> defines = new HashMap<>();

            List<String> args = getArgumentsList();
            for (String arg : args) {
                if (arg.startsWith("-D")) {
                    String define = arg.substring(2);
                    int eqIndex = define.indexOf('=');

                    if (eqIndex > 0) {
                        // -DNAME=VALUE
                        String name = define.substring(0, eqIndex);
                        String value = define.substring(eqIndex + 1);
                        defines.put(name, value);
                    } else {
                        // -DNAME (no value, defined as empty)
                        defines.put(define, "");
                    }
                }
            }

            return defines;
        }

        /**
         * 获取C++标准(-std选项)
         */
        public String getStandard() {
            List<String> args = getArgumentsList();

            for (String arg : args) {
                if (arg.startsWith("-std=")) {
                    return arg.substring(5);  // "c++17", "c11", etc.
                }
            }

            return null;
        }

        /**
         * 获取参数列表
         * 优先使用arguments字段,否则解析command字符串
         */
        private List<String> getArgumentsList() {
            if (arguments != null && arguments.length > 0) {
                return Arrays.asList(arguments);
            }

            if (command != null && !command.isEmpty()) {
                // 简化版命令解析(不处理引号)
                return Arrays.asList(command.split("\\s+"));
            }

            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return String.format("CompileCommand[file=%s, dir=%s]", file, directory);
        }
    }

    /**
     * 编译统计信息
     */
    public static class CompilationStatistics {
        private final int totalCommands;
        private final int uniqueFiles;
        private final int includePaths;
        private final int defines;

        public CompilationStatistics(int totalCommands, int uniqueFiles,
                                    int includePaths, int defines) {
            this.totalCommands = totalCommands;
            this.uniqueFiles = uniqueFiles;
            this.includePaths = includePaths;
            this.defines = defines;
        }

        public int getTotalCommands() {
            return totalCommands;
        }

        public int getUniqueFiles() {
            return uniqueFiles;
        }

        public int getIncludePaths() {
            return includePaths;
        }

        public int getDefines() {
            return defines;
        }

        @Override
        public String toString() {
            return String.format(
                "CompilationStatistics[commands=%d, files=%d, includes=%d, defines=%d]",
                totalCommands, uniqueFiles, includePaths, defines
            );
        }
    }
}
