package com.devflow.api.sandbox.config;

import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sandbox")
public class SandboxProperties {

    private boolean enabled = true;
    private Duration executionTimeout = Duration.ofMinutes(2);
    private long cpuPeriod = 100_000L;
    private long cpuQuota = 200_000L;
    private long memoryBytes = 1_073_741_824L; // 1GB
    private long maxProcesses = 256;
    private long maxWorkspaceBytes = 104_857_600L; // 100MB
    private int maxFiles = 1_000;
    private long maxIndividualFileBytes = 10_485_760L; // 10MB
    private int maxOutputCharacters = 20_000;
    private String workspaceRoot = System.getProperty("java.io.tmpdir") + File.separator + "devflow-sandbox-workspaces";
    private String executorUrl = "http://localhost:8089";
    private String executorToken = "devflow-sandbox-secret-token";
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 130_000;
    private Set<SandboxExecutionProfile> allowedProfiles = Set.of(SandboxExecutionProfile.JAVA_MAVEN_TEST);

    @PostConstruct
    public void validate() {
        if (executorUrl == null || executorUrl.isBlank()) {
            throw new IllegalStateException("Sandbox executor URL must be configured");
        }
        if (executorToken == null || executorToken.isBlank()) {
            throw new IllegalStateException("Sandbox executor authentication token must be configured");
        }
        if (executionTimeout == null || executionTimeout.isNegative() || executionTimeout.isZero()
                || executionTimeout.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalStateException("Sandbox execution timeout must be between 1s and 30m");
        }
        if (cpuPeriod <= 0 || cpuQuota <= 0) {
            throw new IllegalStateException("Sandbox CPU quota and period must be positive");
        }
        if (memoryBytes < 16 * 1024 * 1024L || memoryBytes > 16L * 1024 * 1024 * 1024L) {
            throw new IllegalStateException("Sandbox memory limit must be between 16MB and 16GB");
        }
        if (maxProcesses <= 0 || maxProcesses > 10_000) {
            throw new IllegalStateException("Sandbox max processes must be between 1 and 10000");
        }
        if (maxWorkspaceBytes <= 0 || maxWorkspaceBytes > 1024 * 1024 * 1024L) {
            throw new IllegalStateException("Sandbox max workspace bytes must be between 1B and 1GB");
        }
        if (maxFiles <= 0 || maxFiles > 100_000) {
            throw new IllegalStateException("Sandbox max files must be between 1 and 100000");
        }
        if (maxIndividualFileBytes <= 0 || maxIndividualFileBytes > 100 * 1024 * 1024L) {
            throw new IllegalStateException("Sandbox max individual file bytes must be between 1B and 100MB");
        }
        if (maxOutputCharacters <= 0 || maxOutputCharacters > 20_000) {
            throw new IllegalStateException("Sandbox max output characters must be between 1 and 20000");
        }
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            throw new IllegalStateException("Sandbox workspace root must be configured");
        }
        if (allowedProfiles == null || allowedProfiles.isEmpty()) {
            throw new IllegalStateException("Sandbox allowed profiles must not be empty");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout;
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

    public long getMaxWorkspaceBytes() {
        return maxWorkspaceBytes;
    }

    public void setMaxWorkspaceBytes(long maxWorkspaceBytes) {
        this.maxWorkspaceBytes = maxWorkspaceBytes;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public long getMaxIndividualFileBytes() {
        return maxIndividualFileBytes;
    }

    public void setMaxIndividualFileBytes(long maxIndividualFileBytes) {
        this.maxIndividualFileBytes = maxIndividualFileBytes;
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

    public String getExecutorUrl() {
        return executorUrl;
    }

    public void setExecutorUrl(String executorUrl) {
        this.executorUrl = executorUrl;
    }

    public String getExecutorToken() {
        return executorToken;
    }

    public void setExecutorToken(String executorToken) {
        this.executorToken = executorToken;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public Set<SandboxExecutionProfile> getAllowedProfiles() {
        return allowedProfiles;
    }

    public void setAllowedProfiles(Set<SandboxExecutionProfile> allowedProfiles) {
        this.allowedProfiles = allowedProfiles;
    }
}
