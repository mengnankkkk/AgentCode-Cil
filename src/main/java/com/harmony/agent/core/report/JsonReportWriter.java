package com.harmony.agent.core.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.harmony.agent.core.model.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * JSON Report Writer - Saves scan results in machine-readable JSON format
 * Used for downstream processing by suggest/refactor commands
 */
public class JsonReportWriter {

    private static final Logger logger = LoggerFactory.getLogger(JsonReportWriter.class);

    private final Gson gson;

    public JsonReportWriter() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

        logger.info("JsonReportWriter initialized");
    }

    /**
     * Write scan result to JSON file
     *
     * @param result Scan result to write
     * @param outputFile Output JSON file path
     * @throws IOException if writing fails
     */
    public void write(ScanResult result, Path outputFile) throws IOException {
        logger.info("Writing JSON report to: {}", outputFile);

        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            gson.toJson(result, writer);
            writer.flush();
        }

        long fileSize = outputFile.toFile().length();
        logger.info("JSON report written successfully: {} bytes", fileSize);
        
        if (fileSize == 0) {
            logger.error("JSON report file is empty! Check if ScanResult is null or has no data");
        }
    }

    /**
     * Read scan result from JSON file
     *
     * @param inputFile Input JSON file path
     * @return Scan result
     * @throws IOException if reading fails
     */
    public ScanResult read(Path inputFile) throws IOException {
        logger.info("Reading JSON report from: {}", inputFile);

        ScanResult result;
        try (FileReader reader = new FileReader(inputFile.toFile())) {
            result = gson.fromJson(reader, ScanResult.class);
        }

        logger.info("JSON report read successfully: {} issues", result.getTotalIssueCount());
        return result;
    }
}
