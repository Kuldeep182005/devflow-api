package com.devflow.api.github.mutation.domain;

import java.util.Set;

public enum GithubMutationStatus {
    QUEUED,
    RUNNING,
    BRANCH_CREATED,
    COMMITTED,
    PR_CREATED,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED;
    }

    public boolean canTransitionTo(GithubMutationStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return switch (this) {
            case QUEUED -> target == RUNNING || target == FAILED;
            case RUNNING -> target == COMMITTED || target == BRANCH_CREATED || target == PR_CREATED || target == SUCCEEDED || target == FAILED;
            case COMMITTED -> target == BRANCH_CREATED || target == PR_CREATED || target == SUCCEEDED || target == FAILED;
            case BRANCH_CREATED -> target == COMMITTED || target == PR_CREATED || target == SUCCEEDED || target == FAILED;
            case PR_CREATED -> target == SUCCEEDED || target == FAILED;
            case SUCCEEDED -> false;
            case FAILED -> target == QUEUED || target == RUNNING;
        };
    }
}
