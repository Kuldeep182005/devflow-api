package com.devflow.api.sandbox.domain;

import java.util.List;

public enum SandboxExecutionProfile {
    JAVA_MAVEN_TEST(
            "maven:3.9.6-eclipse-temurin-21-alpine",
            "/workspace",
            List.of("mvn", "test", "-B", "-DskipDocker=true")
    );

    private final String defaultImage;
    private final String workingDirectory;
    private final List<String> command;

    SandboxExecutionProfile(String defaultImage, String workingDirectory, List<String> command) {
        this.defaultImage = defaultImage;
        this.workingDirectory = workingDirectory;
        this.command = List.copyOf(command);
    }

    public String getDefaultImage() {
        return defaultImage;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public List<String> getCommand() {
        return command;
    }
}
