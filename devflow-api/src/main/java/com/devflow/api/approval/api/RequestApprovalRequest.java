package com.devflow.api.approval.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RequestApprovalRequest(
        @NotNull(message = "proposedChangeId must not be null")
        UUID proposedChangeId
) {
}
