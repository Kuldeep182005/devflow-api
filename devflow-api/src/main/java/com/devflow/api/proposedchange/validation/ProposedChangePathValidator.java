package com.devflow.api.proposedchange.validation;

import com.devflow.api.proposedchange.config.ProposedChangeProperties;
import com.devflow.api.proposedchange.exception.ProposedChangeExceptions;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProposedChangePathValidator {
    private static final Pattern DRIVE_PATH = Pattern.compile("^[A-Za-z]:.*$");

    private final ProposedChangeProperties properties;

    public ProposedChangePathValidator(ProposedChangeProperties properties) {
        this.properties = properties;
    }

    public String validate(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > properties.getMaxPathLength()
                || raw.indexOf('\0') >= 0) {
            throw ProposedChangeExceptions.validation();
        }
        if (raw.startsWith("/") || raw.startsWith("\\") || raw.startsWith("\\\\")
                || DRIVE_PATH.matcher(raw).matches()) {
            throw ProposedChangeExceptions.validation();
        }
        String normalized = raw.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.endsWith("/") || normalized.startsWith("./")
                || normalized.contains("//")) {
            throw ProposedChangeExceptions.validation();
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.indexOf('\n') >= 0 || segment.indexOf('\r') >= 0
                    || segment.chars().anyMatch(Character::isISOControl)) {
                throw ProposedChangeExceptions.validation();
            }
        }
        return normalized;
    }
}
