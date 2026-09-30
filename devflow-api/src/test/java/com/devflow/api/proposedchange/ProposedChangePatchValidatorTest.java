package com.devflow.api.proposedchange;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devflow.api.proposedchange.api.ProposedChangeCommand;
import com.devflow.api.proposedchange.api.ProposedChangeFileCommand;
import com.devflow.api.proposedchange.config.ProposedChangeProperties;
import com.devflow.api.proposedchange.domain.ProposedChangeOperation;
import com.devflow.api.proposedchange.exception.ProposedChangeException;
import com.devflow.api.proposedchange.validation.ProposedChangePatchValidator;
import com.devflow.api.proposedchange.validation.ProposedChangePathValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposedChangePatchValidatorTest {
    private static final String PATCH = """
            diff --git a/README.md b/README.md
            --- a/README.md
            +++ b/README.md
            @@ -1,1 +1,2 @@
             old
            +new
            """;

    private static final String MULTI_FILE_PATCH = """
            diff --git a/README.md b/README.md
            --- a/README.md
            +++ b/README.md
            @@ -1,1 +1,2 @@
             old
            +new
            diff --git a/src/App.java b/src/App.java
            --- a/src/App.java
            +++ b/src/App.java
            @@ -1,2 +1,1 @@
            -obsolete
             kept
            \\ No newline at end of file
            """;

    private static final String APP_FILE_PATCH = """
            diff --git a/src/App.java b/src/App.java
            --- a/src/App.java
            +++ b/src/App.java
            @@ -1,2 +1,1 @@
            -obsolete
             kept
            \\ No newline at end of file
            """;

    @Test
    void acceptsBoundedUnifiedPatch() {
        ProposedChangeProperties properties = new ProposedChangeProperties();
        var validator = new ProposedChangePatchValidator(properties, new ProposedChangePathValidator(properties));
        assertDoesNotThrow(() -> validator.validate(command(PATCH, 1, 0)));
    }

    @Test
    void acceptsMultiFilePatchWithContextAndNoNewline() {
        ProposedChangeProperties properties = new ProposedChangeProperties();
        var validator = new ProposedChangePatchValidator(properties, new ProposedChangePathValidator(properties));
        assertDoesNotThrow(() -> validator.validate(new ProposedChangeCommand(
                "Multi file", "Two files changed", "main", MULTI_FILE_PATCH,
                List.of(
                        new ProposedChangeFileCommand("README.md", ProposedChangeOperation.MODIFY, 1, 0, PATCH.trim()),
                        new ProposedChangeFileCommand("src/App.java", ProposedChangeOperation.MODIFY, 0, 1, APP_FILE_PATCH.trim())))));
    }

    @Test
    void rejectsMalformedUnifiedDiffs() {
        ProposedChangeProperties properties = new ProposedChangeProperties();
        var validator = new ProposedChangePatchValidator(properties, new ProposedChangePathValidator(properties));

        // Arbitrary text
        assertThrows(ProposedChangeException.class, () -> validator.validate(command("arbitrary text", 1, 0)));

        // Missing @@ hunks
        String noHunks = """
                diff --git a/README.md b/README.md
                --- a/README.md
                +++ b/README.md
                """;
        assertThrows(ProposedChangeException.class, () -> validator.validate(command(noHunks, 0, 0)));

        // Missing --- / +++
        String noMarkers = """
                diff --git a/README.md b/README.md
                @@ -1,1 +1,2 @@
                 old
                +new
                """;
        assertThrows(ProposedChangeException.class, () -> validator.validate(command(noMarkers, 1, 0)));

        // Arbitrary text between diff header and hunks
        String garbageHeader = """
                diff --git a/README.md b/README.md
                arbitrary text here
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,2 @@
                 old
                +new
                """;
        assertThrows(ProposedChangeException.class, () -> validator.validate(command(garbageHeader, 1, 0)));

        // Arbitrary text inside hunk
        String garbageHunk = """
                diff --git a/README.md b/README.md
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,2 @@
                 old
                random text without plus or minus
                +new
                """;
        assertThrows(ProposedChangeException.class, () -> validator.validate(command(garbageHunk, 1, 0)));
    }

    @Test
    void enforcesDeclaredAdditionsAndDeletionsMatchActualDiff() {
        ProposedChangeProperties properties = new ProposedChangeProperties();
        var validator = new ProposedChangePatchValidator(properties, new ProposedChangePathValidator(properties));

        // Declared additions 2, actual 1
        assertThrows(ProposedChangeException.class, () -> validator.validate(command(PATCH, 2, 0)));
        // Declared deletions 1, actual 0
        assertThrows(ProposedChangeException.class, () -> validator.validate(command(PATCH, 1, 1)));
    }

    @Test
    void enforcesFileCountAndLineLimits() {
        ProposedChangeProperties properties = new ProposedChangeProperties();
        properties.setMaxChangedFiles(1);
        var validator = new ProposedChangePatchValidator(properties, new ProposedChangePathValidator(properties));
        assertThrows(ProposedChangeException.class, () -> validator.validate(new ProposedChangeCommand(
                "title", "description", "main", MULTI_FILE_PATCH,
                List.of(new ProposedChangeFileCommand("README.md", ProposedChangeOperation.MODIFY, 1, 0, PATCH.trim()),
                        new ProposedChangeFileCommand("src/App.java", ProposedChangeOperation.MODIFY, 0, 1, APP_FILE_PATCH.trim())))));

        // Character limit
        ProposedChangeProperties charProps = new ProposedChangeProperties();
        charProps.setMaxPatchCharacters(20);
        var charValidator = new ProposedChangePatchValidator(charProps, new ProposedChangePathValidator(charProps));
        assertThrows(ProposedChangeException.class, () -> charValidator.validate(command(PATCH, 1, 0)));

        // Byte limit
        ProposedChangeProperties byteProps = new ProposedChangeProperties();
        byteProps.setMaxPatchBytes(20);
        var byteValidator = new ProposedChangePatchValidator(byteProps, new ProposedChangePathValidator(byteProps));
        assertThrows(ProposedChangeException.class, () -> byteValidator.validate(command(PATCH, 1, 0)));

        // Per-file patch limit
        ProposedChangeProperties filePatchProps = new ProposedChangeProperties();
        filePatchProps.setMaxFilePatchCharacters(10);
        var filePatchValidator = new ProposedChangePatchValidator(filePatchProps, new ProposedChangePathValidator(filePatchProps));
        assertThrows(ProposedChangeException.class, () -> filePatchValidator.validate(command(PATCH, 1, 0)));

        // Total additions limit
        ProposedChangeProperties addProps = new ProposedChangeProperties();
        addProps.setMaxAddedLines(0);
        var addValidator = new ProposedChangePatchValidator(addProps, new ProposedChangePathValidator(addProps));
        assertThrows(ProposedChangeException.class, () -> addValidator.validate(command(PATCH, 1, 0)));

        // Total deletions limit
        ProposedChangeProperties delProps = new ProposedChangeProperties();
        delProps.setMaxRemovedLines(0);
        var delValidator = new ProposedChangePatchValidator(delProps, new ProposedChangePathValidator(delProps));
        String delPatch = """
                diff --git a/README.md b/README.md
                --- a/README.md
                +++ b/README.md
                @@ -1,2 +1,1 @@
                 old
                -obsolete
                """;
        assertThrows(ProposedChangeException.class, () -> delValidator.validate(command(delPatch, 0, 1)));
    }

    @Test
    void rejectsDuplicatePathsAndEmptyInputs() {
        ProposedChangeProperties properties = new ProposedChangeProperties();
        var validator = new ProposedChangePatchValidator(properties, new ProposedChangePathValidator(properties));

        // Duplicate path
        assertThrows(ProposedChangeException.class, () -> validator.validate(new ProposedChangeCommand(
                "title", "description", "main", PATCH,
                List.of(new ProposedChangeFileCommand("README.md", ProposedChangeOperation.MODIFY, 1, 0, PATCH.trim()),
                        new ProposedChangeFileCommand("README.md", ProposedChangeOperation.MODIFY, 1, 0, PATCH.trim())))));

        // Duplicate path via backslash normalization
        assertThrows(ProposedChangeException.class, () -> validator.validate(new ProposedChangeCommand(
                "title", "description", "main", PATCH,
                List.of(new ProposedChangeFileCommand("src/App.java", ProposedChangeOperation.MODIFY, 1, 0, PATCH.trim()),
                        new ProposedChangeFileCommand("src\\App.java", ProposedChangeOperation.MODIFY, 1, 0, PATCH.trim())))));

        // Empty patch
        assertThrows(ProposedChangeException.class, () -> validator.validate(command("", 0, 0)));
        assertThrows(ProposedChangeException.class, () -> validator.validate(command("   ", 0, 0)));

        // Empty file list
        assertThrows(ProposedChangeException.class, () -> validator.validate(new ProposedChangeCommand(
                "title", "description", "main", PATCH, List.of())));
    }

    @Test
    void enforcesFilePatchConsistencyWithOverallPatch() {
        ProposedChangeProperties properties = new ProposedChangeProperties();
        var validator = new ProposedChangePatchValidator(properties, new ProposedChangePathValidator(properties));

        // filePatch arbitrary text
        assertThrows(ProposedChangeException.class, () -> validator.validate(new ProposedChangeCommand(
                "title", "description", "main", PATCH,
                List.of(new ProposedChangeFileCommand("README.md", ProposedChangeOperation.MODIFY, 1, 0, "arbitrary text")))));

        // filePatch not contained in overall patch
        String unrelatedPatch = """
                diff --git a/README.md b/README.md
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,2 @@
                 other
                +content
                """;
        assertThrows(ProposedChangeException.class, () -> validator.validate(new ProposedChangeCommand(
                "title", "description", "main", PATCH,
                List.of(new ProposedChangeFileCommand("README.md", ProposedChangeOperation.MODIFY, 1, 0, unrelatedPatch.trim())))));

        // filePatch path does not match file.path
        String mismatchedPathPatch = """
                diff --git a/other.md b/other.md
                --- a/other.md
                +++ b/other.md
                @@ -1,1 +1,2 @@
                 old
                +new
                """;
        assertThrows(ProposedChangeException.class, () -> validator.validate(new ProposedChangeCommand(
                "title", "description", "main", mismatchedPathPatch,
                List.of(new ProposedChangeFileCommand("README.md", ProposedChangeOperation.MODIFY, 1, 0, mismatchedPathPatch.trim())))));
    }

    private ProposedChangeCommand command(String patch, int additions, int deletions) {
        return new ProposedChangeCommand("title", "description", "main", patch,
                List.of(new ProposedChangeFileCommand("README.md", ProposedChangeOperation.MODIFY,
                        additions, deletions, patch.trim())));
    }
}
