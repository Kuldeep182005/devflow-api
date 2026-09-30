package com.devflow.api.proposedchange.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proposed-changes")
public class ProposedChangeProperties {
    private int maxChangedFiles = 50;
    private int maxPathLength = 1000;
    private int maxPatchCharacters = 200_000;
    private int maxPatchBytes = 400_000;
    private int maxAddedLines = 10_000;
    private int maxRemovedLines = 10_000;
    private int maxFilePatchCharacters = 100_000;
    private int maxTitleCharacters = 255;
    private int maxDescriptionCharacters = 4_000;

    public int getMaxChangedFiles() { return maxChangedFiles; }
    public void setMaxChangedFiles(int value) { maxChangedFiles = value; }
    public int getMaxPathLength() { return maxPathLength; }
    public void setMaxPathLength(int value) { maxPathLength = value; }
    public int getMaxPatchCharacters() { return maxPatchCharacters; }
    public void setMaxPatchCharacters(int value) { maxPatchCharacters = value; }
    public int getMaxPatchBytes() { return maxPatchBytes; }
    public void setMaxPatchBytes(int value) { maxPatchBytes = value; }
    public int getMaxAddedLines() { return maxAddedLines; }
    public void setMaxAddedLines(int value) { maxAddedLines = value; }
    public int getMaxRemovedLines() { return maxRemovedLines; }
    public void setMaxRemovedLines(int value) { maxRemovedLines = value; }
    public int getMaxFilePatchCharacters() { return maxFilePatchCharacters; }
    public void setMaxFilePatchCharacters(int value) { maxFilePatchCharacters = value; }
    public int getMaxTitleCharacters() { return maxTitleCharacters; }
    public void setMaxTitleCharacters(int value) { maxTitleCharacters = value; }
    public int getMaxDescriptionCharacters() { return maxDescriptionCharacters; }
    public void setMaxDescriptionCharacters(int value) { maxDescriptionCharacters = value; }

    public void validate() {
        if (maxChangedFiles <= 0 || maxPathLength <= 0 || maxPatchCharacters <= 0
                || maxPatchBytes <= 0 || maxAddedLines <= 0 || maxRemovedLines <= 0
                || maxFilePatchCharacters <= 0 || maxTitleCharacters <= 0
                || maxDescriptionCharacters <= 0) {
            throw new IllegalStateException("Proposed change limits are invalid");
        }
    }
}
