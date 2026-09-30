package com.devflow.api.proposedchange.api;

import com.devflow.api.proposedchange.domain.ProposedChangeOperation;

public record ProposedChangeFileCommand(String path, ProposedChangeOperation operation,
        int additions, int deletions, String filePatch) {
}
