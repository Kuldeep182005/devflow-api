package com.devflow.executor.dto;

import java.util.List;
import java.util.UUID;

public record ExecuteRequestDto(
        UUID executionId,
        UUID proposedChangeId,
        String profile,
        List<ExecutionFileDto> files
) {}
