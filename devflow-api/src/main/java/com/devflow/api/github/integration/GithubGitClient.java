package com.devflow.api.github.integration;

import java.util.List;
import java.util.Optional;

public interface GithubGitClient {

    Optional<GitRef> getRef(String owner, String repo, String ref, String installationToken);

    Optional<GitCommit> getCommit(String owner, String repo, String commitSha, String installationToken);

    Optional<String> getFileContent(String owner, String repo, String path, String ref, String installationToken);

    String createBlob(String owner, String repo, String content, String installationToken);

    String createTree(String owner, String repo, String baseTreeSha, List<GitTreeEntry> entries, String installationToken);

    String createCommit(String owner, String repo, String message, String treeSha, List<String> parentShas, String installationToken);

    GitRef createRef(String owner, String repo, String ref, String commitSha, String installationToken);
}
