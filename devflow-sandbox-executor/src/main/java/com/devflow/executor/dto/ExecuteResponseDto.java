package com.devflow.executor.dto;

import java.util.UUID;

public record ExecuteResponseDto(
        UUID executionId,
        UUID proposedChangeId,
        String status,
        Integer exitCode,
        Long durationMs,
        String stdout,
        String stderr,
        Boolean outputTruncated,
        String failureCode,
        String failureMessage
) {}
