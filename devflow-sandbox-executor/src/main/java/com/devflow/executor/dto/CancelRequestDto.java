package com.devflow.executor.dto;

import java.util.UUID;

public record CancelRequestDto(
        UUID executionId
) {}
