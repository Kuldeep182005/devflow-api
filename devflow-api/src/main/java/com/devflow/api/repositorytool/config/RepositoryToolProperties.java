package com.devflow.api.repositorytool.config;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "repository-tools")
public class RepositoryToolProperties {

    private boolean enabled;
    private int maxFileBytes = 1_048_576;
    private int maxListResults = 500;
    private int maxSearchResults = 200;
    private int maxFilesScanned = 2_000;
    private int maxSnippetCharacters = 240;
    private int maxSearchQueryCharacters = 256;
    private int maxDirectoryDepth = 20;
    private Map<String, Workspace> workspaces = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(int value) { this.maxFileBytes = value; }
    public int getMaxListResults() { return maxListResults; }
    public void setMaxListResults(int value) { this.maxListResults = value; }
    public int getMaxSearchResults() { return maxSearchResults; }
    public void setMaxSearchResults(int value) { this.maxSearchResults = value; }
    public int getMaxFilesScanned() { return maxFilesScanned; }
    public void setMaxFilesScanned(int value) { this.maxFilesScanned = value; }
    public int getMaxSnippetCharacters() { return maxSnippetCharacters; }
    public void setMaxSnippetCharacters(int value) { this.maxSnippetCharacters = value; }
    public int getMaxSearchQueryCharacters() { return maxSearchQueryCharacters; }
    public void setMaxSearchQueryCharacters(int value) { this.maxSearchQueryCharacters = value; }
    public int getMaxDirectoryDepth() { return maxDirectoryDepth; }
    public void setMaxDirectoryDepth(int value) { this.maxDirectoryDepth = value; }
    public Map<String, Workspace> getWorkspaces() { return workspaces; }
    public void setWorkspaces(Map<String, Workspace> workspaces) { this.workspaces = workspaces; }

    public void validate() {
        if (maxFileBytes <= 0 || maxListResults <= 0 || maxSearchResults <= 0
                || maxFilesScanned <= 0 || maxSnippetCharacters <= 0
                || maxSearchQueryCharacters <= 0 || maxDirectoryDepth < 0) {
            throw new IllegalStateException("Repository tool limits are invalid");
        }
        if (workspaces == null) {
            throw new IllegalStateException("Repository tool workspaces are invalid");
        }
        for (Map.Entry<String, Workspace> entry : workspaces.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue().getRoot() == null
                    || !Files.isDirectory(entry.getValue().getRoot())) {
                throw new IllegalStateException("Repository tool workspaces are invalid");
            }
        }
    }

    public static class Workspace {
        private Path root;
        private String branch = "";

        public Path getRoot() { return root; }
        public void setRoot(Path root) { this.root = root; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
    }
}
