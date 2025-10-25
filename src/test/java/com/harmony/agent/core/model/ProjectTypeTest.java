package com.harmony.agent.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ProjectType detection logic
 */
class ProjectTypeTest {

    @Test
    void testDetectCppProjectWithCompileCommands(@TempDir Path tempDir) throws IOException {
        // Create compile_commands.json
        Files.createFile(tempDir.resolve("compile_commands.json"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.C_CPP, type, 
            "Should detect C/C++ project when compile_commands.json exists");
    }

    @Test
    void testDetectCppProjectWithCompileCommandsOverPom(@TempDir Path tempDir) throws IOException {
        // Create both compile_commands.json and pom.xml
        Files.createFile(tempDir.resolve("compile_commands.json"));
        Files.createFile(tempDir.resolve("pom.xml"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.C_CPP, type, 
            "Should prioritize compile_commands.json over pom.xml and detect as C/C++");
    }

    @Test
    void testDetectRustProject(@TempDir Path tempDir) throws IOException {
        // Create Cargo.toml
        Files.createFile(tempDir.resolve("Cargo.toml"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.RUST, type, 
            "Should detect Rust project when Cargo.toml exists");
    }

    @Test
    void testDetectRustProjectOverPom(@TempDir Path tempDir) throws IOException {
        // Create both Cargo.toml and pom.xml
        Files.createFile(tempDir.resolve("Cargo.toml"));
        Files.createFile(tempDir.resolve("pom.xml"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.RUST, type, 
            "Should prioritize Cargo.toml over pom.xml and detect as Rust");
    }

    @Test
    void testDetectJavaProjectWithPom(@TempDir Path tempDir) throws IOException {
        // Create only pom.xml
        Files.createFile(tempDir.resolve("pom.xml"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.JAVA, type, 
            "Should detect Java project when only pom.xml exists");
    }

    @Test
    void testDetectJavaProjectWithGradle(@TempDir Path tempDir) throws IOException {
        // Create only build.gradle
        Files.createFile(tempDir.resolve("build.gradle"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.JAVA, type, 
            "Should detect Java project when only build.gradle exists");
    }

    @Test
    void testDetectCppProjectWithCMakeLists(@TempDir Path tempDir) throws IOException {
        // Create only CMakeLists.txt
        Files.createFile(tempDir.resolve("CMakeLists.txt"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.C_CPP, type, 
            "Should detect C/C++ project when only CMakeLists.txt exists");
    }

    @Test
    void testDetectCppProjectWithMakefile(@TempDir Path tempDir) throws IOException {
        // Create only Makefile
        Files.createFile(tempDir.resolve("Makefile"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.C_CPP, type, 
            "Should detect C/C++ project when only Makefile exists");
    }

    @Test
    void testPriorityOrderCompileCommandsFirst(@TempDir Path tempDir) throws IOException {
        // Create all project indicators
        Files.createFile(tempDir.resolve("compile_commands.json"));
        Files.createFile(tempDir.resolve("Cargo.toml"));
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createFile(tempDir.resolve("CMakeLists.txt"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.C_CPP, type, 
            "Should prioritize compile_commands.json when all indicators present");
    }

    @Test
    void testPriorityOrderCargoSecond(@TempDir Path tempDir) throws IOException {
        // Create Cargo.toml, pom.xml, and CMakeLists.txt (but not compile_commands.json)
        Files.createFile(tempDir.resolve("Cargo.toml"));
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createFile(tempDir.resolve("CMakeLists.txt"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.RUST, type, 
            "Should prioritize Cargo.toml over pom.xml when compile_commands.json absent");
    }

    @Test
    void testPriorityOrderPomThird(@TempDir Path tempDir) throws IOException {
        // Create pom.xml and CMakeLists.txt (but not compile_commands.json or Cargo.toml)
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createFile(tempDir.resolve("CMakeLists.txt"));
        
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.JAVA, type, 
            "Should prioritize pom.xml over CMakeLists.txt");
    }

    @Test
    void testUnknownProjectType(@TempDir Path tempDir) {
        // Empty directory
        ProjectType type = ProjectType.detectFromDirectory(tempDir.toFile());
        
        assertEquals(ProjectType.UNKNOWN, type, 
            "Should return UNKNOWN for directory without project indicators");
    }

    @Test
    void testNullDirectory() {
        ProjectType type = ProjectType.detectFromDirectory(null);
        
        assertEquals(ProjectType.UNKNOWN, type, 
            "Should return UNKNOWN for null directory");
    }

    @Test
    void testNonExistentDirectory() {
        File nonExistent = new File("/tmp/nonexistent-directory-12345");
        
        ProjectType type = ProjectType.detectFromDirectory(nonExistent);
        
        assertEquals(ProjectType.UNKNOWN, type, 
            "Should return UNKNOWN for non-existent directory");
    }

    @Test
    void testFromFileExtension() {
        assertEquals(ProjectType.C_CPP, ProjectType.fromFile("test.c"));
        assertEquals(ProjectType.C_CPP, ProjectType.fromFile("test.cpp"));
        assertEquals(ProjectType.C_CPP, ProjectType.fromFile("test.h"));
        assertEquals(ProjectType.JAVA, ProjectType.fromFile("test.java"));
        assertEquals(ProjectType.RUST, ProjectType.fromFile("test.rs"));
        assertEquals(ProjectType.UNKNOWN, ProjectType.fromFile("test.txt"));
        assertEquals(ProjectType.UNKNOWN, ProjectType.fromFile(null));
    }
}
