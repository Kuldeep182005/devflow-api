package com.devflow.api.repositorytool.api;

public record RepositoryFileEntry(String path, RepositoryFileType type, long sizeBytes) {
}
