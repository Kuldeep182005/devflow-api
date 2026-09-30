package com.devflow.api.proposedchange.api;

import java.util.List;

public record ProposedChangeCommand(String title, String description, String baseRevision,
        String patch, List<ProposedChangeFileCommand> files) {
}
