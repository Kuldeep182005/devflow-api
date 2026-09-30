package com.devflow.api.proposedchange.exception;

import org.springframework.http.HttpStatus;

public final class ProposedChangeExceptions {
    private ProposedChangeExceptions() {}

    public static ProposedChangeException validation() {
        return new ProposedChangeException(HttpStatus.BAD_REQUEST, "PROPOSED_CHANGE_INVALID",
                "The proposed change is invalid");
    }
    public static ProposedChangeException notFound() {
        return new ProposedChangeException(HttpStatus.NOT_FOUND, "PROPOSED_CHANGE_CONTEXT_NOT_FOUND",
                "The proposed change context was not found");
    }
    public static ProposedChangeException unauthorized() {
        return new ProposedChangeException(HttpStatus.NOT_FOUND, "PROPOSED_CHANGE_CONTEXT_NOT_FOUND",
                "The proposed change context was not found");
    }
    public static ProposedChangeException inactiveRepository() {
        return new ProposedChangeException(HttpStatus.CONFLICT, "REPOSITORY_INACTIVE",
                "The repository is inactive");
    }
    public static ProposedChangeException persistence() {
        return new ProposedChangeException(HttpStatus.INTERNAL_SERVER_ERROR, "PROPOSED_CHANGE_PERSISTENCE_FAILED",
                "The proposed change could not be stored");
    }
}
