package com.devflow.api.repositorytool.domain;

import java.nio.file.Path;

public record RepositoryWorkspace(String repositoryKey, Path root, String branch) {
}
