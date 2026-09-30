package com.devflow.executor.dto;

public record ExecutionFileDto(
        String filePath,
        String operation,
        String content,
        String diffContent
) {}
