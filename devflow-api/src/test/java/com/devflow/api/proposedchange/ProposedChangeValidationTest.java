package com.devflow.api.proposedchange;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devflow.api.proposedchange.config.ProposedChangeProperties;
import com.devflow.api.proposedchange.exception.ProposedChangeException;
import com.devflow.api.proposedchange.validation.ProposedChangePathValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProposedChangeValidationTest {
    private ProposedChangePathValidator paths;

    @BeforeEach
    void setUp() {
        ProposedChangeProperties properties = new ProposedChangeProperties();
        paths = new ProposedChangePathValidator(properties);
    }

    @Test
    void acceptsRepositoryRelativeFilePaths() {
        assertDoesNotThrow(() -> paths.validate("src/main/java/com/devflow/api/Foo.java"));
        assertDoesNotThrow(() -> paths.validate("README.md"));
        org.junit.jupiter.api.Assertions.assertEquals("src/main/File.java", paths.validate("src\\main\\File.java"));
    }

    @Test
    void rejectsTraversalAbsoluteAndSpecialPaths() {
        String[] invalid = {
                "../secret", "..\\secret", "../../etc/passwd", "/absolute/path", "\\absolute\\path",
                "C:\\secret.txt", "C:/secret.txt", "D:relative", "\\\\server\\share\\file",
                "repo/../../secret", "safe\\..\\..\\secret", "foo\0bar", "", "directory/"
        };
        for (String path : invalid) {
            assertThrows(ProposedChangeException.class, () -> paths.validate(path), path);
        }
    }

    @Test
    void rejectsControlCharactersAndExcessiveLength() {
        assertThrows(ProposedChangeException.class, () -> paths.validate("safe/\nfile"));
        assertThrows(ProposedChangeException.class, () -> paths.validate("safe/\rfile"));
        assertThrows(ProposedChangeException.class, () -> paths.validate("safe/\tfile"));
        assertThrows(ProposedChangeException.class, () -> paths.validate("safe/\u0007file"));
        ProposedChangeProperties properties = new ProposedChangeProperties();
        properties.setMaxPathLength(4);
        ProposedChangePathValidator limited = new ProposedChangePathValidator(properties);
        assertThrows(ProposedChangeException.class, () -> limited.validate("README.md"));
    }
}
