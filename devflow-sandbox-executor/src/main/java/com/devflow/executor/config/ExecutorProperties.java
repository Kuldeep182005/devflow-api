package com.devflow.executor.config;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "executor")
public class ExecutorProperties {

    private String token = "devflow-sandbox-secret-token";
    private int timeoutSeconds = 120;
    private long cpuPeriod = 100_000L;
    private long cpuQuota = 100_000L;
    private long memoryBytes = 1_073_741_824L; // 1GB
    private long maxProcesses = 256;
    private int maxOutputCharacters = 20_000;
    private String workspaceRoot = "target" + File.separator + "executor-sandboxes";
    private int maxWorkspaceFiles = 5_000;
    private long maxWorkspaceBytes = 52_428_800L; // 50MB
    private long maxFileSizeBytes = 5_242_880L; // 5MB
    private String pinnedImage = "maven:3.9.9-eclipse-temurin-21";
    private Set<String> allowedProfiles = Set.of("JAVA_MAVEN_TEST", "SANDBOX_VERIFICATION_TEST");

    @PostConstruct
    public void validate() {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Executor secret token must be configured");
        }
        if (timeoutSeconds <= 0 || timeoutSeconds > 1800) {
            throw new IllegalStateException("Executor timeout must be between 1 and 1800 seconds");
        }
        if (cpuPeriod <= 0 || cpuQuota <= 0) {
            throw new IllegalStateException("Executor CPU quota and period must be positive");
        }
        if (memoryBytes < 16 * 1024 * 1024L || memoryBytes > 16L * 1024 * 1024 * 1024L) {
            throw new IllegalStateException("Executor memory limit must be between 16MB and 16GB");
        }
        if (maxProcesses <= 0 || maxProcesses > 10_000) {
            throw new IllegalStateException("Executor max processes must be between 1 and 10000");
        }
        if (maxOutputCharacters <= 0 || maxOutputCharacters > 20_000) {
            throw new IllegalStateException("Executor max output characters must be between 1 and 20000");
        }
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            throw new IllegalStateException("Executor workspace root must be configured");
        }
        if (maxWorkspaceFiles <= 0 || maxWorkspaceFiles > 100_000) {
            throw new IllegalStateException("Executor max workspace files must be between 1 and 100000");
        }
        if (maxWorkspaceBytes <= 0 || maxWorkspaceBytes > 1024 * 1024 * 1024L) {
            throw new IllegalStateException("Executor max workspace bytes must be between 1B and 1GB");
        }
        if (maxFileSizeBytes <= 0 || maxFileSizeBytes > 100 * 1024 * 1024L) {
            throw new IllegalStateException("Executor max file size bytes must be between 1B and 100MB");
        }
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getCpuPeriod() {
        return cpuPeriod;
    }

    public void setCpuPeriod(long cpuPeriod) {
        this.cpuPeriod = cpuPeriod;
    }

    public long getCpuQuota() {
        return cpuQuota;
    }

    public void setCpuQuota(long cpuQuota) {
        this.cpuQuota = cpuQuota;
    }

    public long getMemoryBytes() {
        return memoryBytes;
    }

    public void setMemoryBytes(long memoryBytes) {
        this.memoryBytes = memoryBytes;
    }

    public long getMaxProcesses() {
        return maxProcesses;
    }

    public void setMaxProcesses(long maxProcesses) {
        this.maxProcesses = maxProcesses;
    }

    public int getMaxOutputCharacters() {
        return maxOutputCharacters;
    }

    public void setMaxOutputCharacters(int maxOutputCharacters) {
        this.maxOutputCharacters = maxOutputCharacters;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public int getMaxWorkspaceFiles() {
        return maxWorkspaceFiles;
    }

    public void setMaxWorkspaceFiles(int maxWorkspaceFiles) {
        this.maxWorkspaceFiles = maxWorkspaceFiles;
    }

    public long getMaxWorkspaceBytes() {
        return maxWorkspaceBytes;
    }

    public void setMaxWorkspaceBytes(long maxWorkspaceBytes) {
        this.maxWorkspaceBytes = maxWorkspaceBytes;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String getPinnedImage() {
        return pinnedImage;
    }

    public void setPinnedImage(String pinnedImage) {
        this.pinnedImage = pinnedImage;
    }

    public Set<String> getAllowedProfiles() {
        return allowedProfiles;
    }

    public void setAllowedProfiles(Set<String> allowedProfiles) {
        this.allowedProfiles = allowedProfiles;
    }
}
