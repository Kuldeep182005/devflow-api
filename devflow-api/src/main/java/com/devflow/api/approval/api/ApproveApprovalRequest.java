package com.devflow.api.approval.api;

import jakarta.validation.constraints.Size;

public record ApproveApprovalRequest(
        @Size(max = 1000, message = "comment must not exceed 1000 characters")
        String comment
) {
}
