package com.devflow.api.repositorytool.api;

public record RepositoryReadResult(String path, String content, long sizeBytes) {
}
