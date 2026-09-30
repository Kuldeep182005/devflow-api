package com.devflow.api.sandbox.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java unified diff patch applier.
 * Does NOT invoke git, shell, or any external process.
 */
public final class UnifiedDiffApplier {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private UnifiedDiffApplier() {
    }

    /**
     * Extracts content of a newly added file from its unified diff patch.
     */
    public static String extractAddedContent(String diffText) {
        if (diffText == null) {
            return "";
        }
        String[] lines = diffText.split("\\r?\\n", -1);
        List<String> contentLines = new ArrayList<>();
        boolean inHunk = false;

        for (String line : lines) {
            if (line.startsWith("diff --git ") || line.startsWith("--- ")
                    || line.startsWith("+++ ") || line.startsWith("index ")
                    || line.startsWith("new file mode ") || line.startsWith("old mode ")) {
                inHunk = false;
                continue;
            }
            if (line.startsWith("@@ ")) {
                inHunk = true;
                continue;
            }
            if (inHunk) {
                if (line.startsWith("+")) {
                    contentLines.add(line.substring(1));
                } else if (line.startsWith(" ")) {
                    contentLines.add(line.substring(1));
                }
            }
        }
        return String.join("\n", contentLines);
    }

    /**
     * Applies a unified diff to existing lines of a file.
     */
    public static String applyPatch(String originalContent, String diffText) {
        if (diffText == null || diffText.isBlank()) {
            return originalContent;
        }
        String[] orig = originalContent == null || originalContent.isEmpty()
                ? new String[0]
                : originalContent.split("\\r?\\n", -1);
        List<String> currentLines = new ArrayList<>(List.of(orig));

        String[] diffLines = diffText.split("\\r?\\n", -1);
        int i = 0;
        int lineAdjustment = 0;

        while (i < diffLines.length) {
            String line = diffLines[i];
            if (!line.startsWith("@@ ")) {
                i++;
                continue;
            }

            Matcher matcher = HUNK_HEADER.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid diff hunk header: " + line);
            }

            int oldStart = Integer.parseInt(matcher.group(1));
            int targetIndex = (oldStart - 1) + lineAdjustment;
            i++;

            List<String> expectedOld = new ArrayList<>();
            List<String> replacement = new ArrayList<>();

            while (i < diffLines.length && !diffLines[i].startsWith("@@ ") && !diffLines[i].startsWith("diff --git ")) {
                String hLine = diffLines[i];
                if (hLine.startsWith(" ")) {
                    expectedOld.add(hLine.substring(1));
                    replacement.add(hLine.substring(1));
                } else if (hLine.startsWith("-")) {
                    expectedOld.add(hLine.substring(1));
                } else if (hLine.startsWith("+")) {
                    replacement.add(hLine.substring(1));
                } else if (hLine.startsWith("\\ No newline at end of file")) {
                    // Ignore diff metadata marker
                } else {
                    break;
                }
                i++;
            }

            // Verify context in current lines
            if (targetIndex < 0 || targetIndex > currentLines.size()) {
                throw new IllegalStateException("Hunk target line out of range: " + targetIndex);
            }

            for (int k = 0; k < expectedOld.size(); k++) {
                int checkIdx = targetIndex + k;
                if (checkIdx >= currentLines.size() || !currentLines.get(checkIdx).equals(expectedOld.get(k))) {
                    throw new IllegalStateException("Context mismatch at line " + (checkIdx + 1));
                }
            }

            // Remove old lines
            for (int k = 0; k < expectedOld.size(); k++) {
                currentLines.remove(targetIndex);
            }

            // Insert new lines
            for (int k = 0; k < replacement.size(); k++) {
                currentLines.add(targetIndex + k, replacement.get(k));
            }

            lineAdjustment += (replacement.size() - expectedOld.size());
        }

        return String.join("\n", currentLines);
    }
}
