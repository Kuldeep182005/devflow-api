package com.devflow.api.task.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTaskRequest(
        @NotNull UUID repositoryId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description) {
}

