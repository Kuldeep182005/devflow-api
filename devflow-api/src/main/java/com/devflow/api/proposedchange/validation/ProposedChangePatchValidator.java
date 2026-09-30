package com.devflow.api.proposedchange.validation;

import com.devflow.api.proposedchange.api.ProposedChangeCommand;
import com.devflow.api.proposedchange.api.ProposedChangeFileCommand;
import com.devflow.api.proposedchange.config.ProposedChangeProperties;
import com.devflow.api.proposedchange.exception.ProposedChangeExceptions;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProposedChangePatchValidator {
    private final ProposedChangeProperties properties;
    private final ProposedChangePathValidator pathValidator;

    public ProposedChangePatchValidator(ProposedChangeProperties properties,
            ProposedChangePathValidator pathValidator) {
        this.properties = properties;
        this.pathValidator = pathValidator;
    }

    public ValidatedPatch validate(ProposedChangeCommand command) {
        if (command == null || command.patch() == null || command.patch().isBlank()
                || command.files() == null || command.files().isEmpty()
                || command.patch().length() > properties.getMaxPatchCharacters()
                || command.patch().getBytes(StandardCharsets.UTF_8).length > properties.getMaxPatchBytes()) {
            throw ProposedChangeExceptions.validation();
        }
        if (command.files().size() > properties.getMaxChangedFiles()) {
            throw ProposedChangeExceptions.validation();
        }

        Set<String> paths = new HashSet<>();
        int totalAdditions = 0;
        int totalDeletions = 0;

        for (ProposedChangeFileCommand file : command.files()) {
            if (file == null || file.operation() == null || file.filePatch() == null
                    || file.filePatch().isBlank() || file.filePatch().length() > properties.getMaxFilePatchCharacters()
                    || file.additions() < 0 || file.deletions() < 0) {
                throw ProposedChangeExceptions.validation();
            }
            String path = pathValidator.validate(file.path());
            if (!paths.add(path)) {
                throw ProposedChangeExceptions.validation();
            }

            // File patch consistency: must be contained in the overall patch
            String trimmedFilePatch = file.filePatch().trim();
            if (!command.patch().contains(trimmedFilePatch)) {
                throw ProposedChangeExceptions.validation();
            }

            // Validate the per-file patch diff structure and check declared additions/deletions
            int[] fileCounts = validateFileDiff(trimmedFilePatch, path);
            if (fileCounts[0] != file.additions() || fileCounts[1] != file.deletions()) {
                throw ProposedChangeExceptions.validation();
            }

            totalAdditions = Math.addExact(totalAdditions, file.additions());
            totalDeletions = Math.addExact(totalDeletions, file.deletions());
        }

        if (totalAdditions > properties.getMaxAddedLines() || totalDeletions > properties.getMaxRemovedLines()) {
            throw ProposedChangeExceptions.validation();
        }

        int[] patchCounts = validateUnifiedStructure(command.patch(), paths);
        if (patchCounts[0] != totalAdditions || patchCounts[1] != totalDeletions) {
            throw ProposedChangeExceptions.validation();
        }

        return new ValidatedPatch(paths, totalAdditions, totalDeletions);
    }

    private int[] validateFileDiff(String filePatch, String expectedPath) {
        String[] lines = filePatch.split("\\r?\\n", -1);
        int additions = 0;
        int deletions = 0;
        boolean sawHunk = false;
        boolean inHunk = false;
        boolean sawHeader = false;
        boolean sawOldFile = false;
        boolean sawNewFile = false;

        for (String line : lines) {
            if (line.startsWith("diff --git ")) {
                if (sawHeader) {
                    throw ProposedChangeExceptions.validation();
                }
                validateDiffGitHeader(line, expectedPath);
                sawHeader = true;
                inHunk = false;
            } else if (line.startsWith("--- ")) {
                if (!sawHeader || sawOldFile || sawNewFile) {
                    throw ProposedChangeExceptions.validation();
                }
                sawOldFile = true;
                inHunk = false;
            } else if (line.startsWith("+++ ")) {
                if (!sawOldFile || sawNewFile) {
                    throw ProposedChangeExceptions.validation();
                }
                sawNewFile = true;
                inHunk = false;
            } else if (line.startsWith("index ") || line.startsWith("new file mode ")
                    || line.startsWith("deleted file mode ") || line.startsWith("similarity index ")
                    || line.startsWith("rename from ") || line.startsWith("rename to ")
                    || line.startsWith("old mode ") || line.startsWith("new mode ")) {
                if (!sawHeader || sawOldFile || sawNewFile) {
                    throw ProposedChangeExceptions.validation();
                }
                inHunk = false;
            } else if (line.startsWith("@@ ")) {
                if (!sawNewFile || !line.matches("@@ -\\d+(,\\d+)? \\+\\d+(,\\d+)? @@.*")) {
                    throw ProposedChangeExceptions.validation();
                }
                sawHunk = true;
                inHunk = true;
            } else if (inHunk) {
                if (line.isEmpty() || line.charAt(0) == ' ' || line.startsWith("\\ No newline at end of file")) {
                    // Valid context line
                } else if (line.startsWith("+") && !line.startsWith("+++")) {
                    additions++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    deletions++;
                } else {
                    throw ProposedChangeExceptions.validation();
                }
            } else {
                throw ProposedChangeExceptions.validation();
            }
        }

        if (!sawHunk || !sawNewFile) {
            throw ProposedChangeExceptions.validation();
        }
        return new int[]{additions, deletions};
    }

    private void validateDiffGitHeader(String line, String expectedPath) {
        String[] parts = line.split(" ");
        if (parts.length != 4 || !parts[0].equals("diff") || !parts[1].equals("--git")) {
            throw ProposedChangeExceptions.validation();
        }
        if (!parts[2].startsWith("a/") || !parts[3].startsWith("b/")) {
            throw ProposedChangeExceptions.validation();
        }
        String pathA = parts[2].substring(2);
        String pathB = parts[3].substring(2);
        if (!pathA.equals(expectedPath) && !pathB.equals(expectedPath)) {
            throw ProposedChangeExceptions.validation();
        }
    }

    private int[] validateUnifiedStructure(String patch, Set<String> paths) {
        String[] lines = patch.split("\\r?\\n", -1);
        Set<String> seenPaths = new HashSet<>();
        int additions = 0;
        int deletions = 0;
        boolean sawHunkInFile = false;
        boolean inHunk = false;
        boolean sawOldFile = false;
        boolean sawNewFile = false;
        int fileCount = 0;

        for (String line : lines) {
            if (line.startsWith("diff --git ")) {
                if (fileCount > 0 && (!sawHunkInFile || !sawNewFile)) {
                    throw ProposedChangeExceptions.validation();
                }
                fileCount++;
                sawHunkInFile = false;
                sawOldFile = false;
                sawNewFile = false;
                inHunk = false;

                String[] parts = line.split(" ");
                if (parts.length != 4 || !parts[0].equals("diff") || !parts[1].equals("--git")) {
                    throw ProposedChangeExceptions.validation();
                }
                if (!parts[2].startsWith("a/") || !parts[3].startsWith("b/")) {
                    throw ProposedChangeExceptions.validation();
                }
                String pathA = parts[2].substring(2);
                String pathB = parts[3].substring(2);
                String matchedPath = null;
                if (paths.contains(pathA)) {
                    matchedPath = pathA;
                } else if (paths.contains(pathB)) {
                    matchedPath = pathB;
                } else {
                    throw ProposedChangeExceptions.validation();
                }
                if (!seenPaths.add(matchedPath)) {
                    throw ProposedChangeExceptions.validation();
                }
            } else if (line.startsWith("--- ")) {
                if (fileCount == 0 || sawOldFile || sawNewFile) {
                    throw ProposedChangeExceptions.validation();
                }
                sawOldFile = true;
                inHunk = false;
            } else if (line.startsWith("+++ ")) {
                if (!sawOldFile || sawNewFile) {
                    throw ProposedChangeExceptions.validation();
                }
                sawNewFile = true;
                inHunk = false;
            } else if (line.startsWith("index ") || line.startsWith("new file mode ")
                    || line.startsWith("deleted file mode ") || line.startsWith("similarity index ")
                    || line.startsWith("rename from ") || line.startsWith("rename to ")
                    || line.startsWith("old mode ") || line.startsWith("new mode ")) {
                if (fileCount == 0 || sawOldFile || sawNewFile) {
                    throw ProposedChangeExceptions.validation();
                }
                inHunk = false;
            } else if (line.startsWith("@@ ")) {
                if (!sawNewFile || !line.matches("@@ -\\d+(,\\d+)? \\+\\d+(,\\d+)? @@.*")) {
                    throw ProposedChangeExceptions.validation();
                }
                sawHunkInFile = true;
                inHunk = true;
            } else if (inHunk) {
                if (line.isEmpty() || line.charAt(0) == ' ' || line.startsWith("\\ No newline at end of file")) {
                    // Valid context line
                } else if (line.startsWith("+") && !line.startsWith("+++")) {
                    additions++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    deletions++;
                } else {
                    throw ProposedChangeExceptions.validation();
                }
            } else {
                throw ProposedChangeExceptions.validation();
            }
        }

        if (fileCount == 0 || !sawHunkInFile || !sawNewFile || !seenPaths.equals(paths)) {
            throw ProposedChangeExceptions.validation();
        }
        return new int[]{additions, deletions};
    }

    public record ValidatedPatch(Set<String> paths, int additions, int deletions) {
    }
}
