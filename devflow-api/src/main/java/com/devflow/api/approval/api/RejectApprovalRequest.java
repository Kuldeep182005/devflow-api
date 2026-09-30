package com.devflow.api.approval.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectApprovalRequest(
        @NotBlank(message = "reason must not be blank")
        @Size(max = 1000, message = "reason must not exceed 1000 characters")
        String reason
) {
}
