package com.harmony.agent.core.model;

/**
 * Represents a location in source code
 */
public class CodeLocation {
    private final String filePath;
    private final int lineNumber;
    private final int columnNumber;
    private final String snippet;

    public CodeLocation(String filePath, int lineNumber, int columnNumber, String snippet) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.snippet = snippet;
    }

    public CodeLocation(String filePath, int lineNumber) {
        this(filePath, lineNumber, 0, null);
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public String getSnippet() {
        return snippet;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(filePath);
        if (lineNumber > 0) {
            sb.append(":").append(lineNumber);
            if (columnNumber > 0) {
                sb.append(":").append(columnNumber);
            }
        }
        return sb.toString();
    }
}
