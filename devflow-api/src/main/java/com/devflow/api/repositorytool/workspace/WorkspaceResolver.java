package com.devflow.api.repositorytool.workspace;

import com.devflow.api.repositorytool.config.RepositoryToolProperties;
import com.devflow.api.repositorytool.domain.RepositoryWorkspace;
import com.devflow.api.repositorytool.exception.RepositoryToolExceptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceResolver {

    private final RepositoryToolProperties properties;

    public WorkspaceResolver(RepositoryToolProperties properties) {
        this.properties = properties;
        properties.validate();
    }

    public RepositoryWorkspace resolve(UUID repositoryId) {
        RepositoryToolProperties.Workspace configured =
                properties.getWorkspaces().get(repositoryId.toString());
        if (configured == null || configured.getRoot() == null) {
            throw RepositoryToolExceptions.workspaceUnavailable();
        }
        Path root = configured.getRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw RepositoryToolExceptions.workspaceUnavailable();
        }
        try {
            return new RepositoryWorkspace(repositoryId.toString(), root.toRealPath(), safeBranch(configured.getBranch()));
        } catch (java.io.IOException exception) {
            throw RepositoryToolExceptions.workspaceUnavailable();
        }
    }

    private String safeBranch(String branch) {
        return branch == null ? "" : branch.length() > 255 ? branch.substring(0, 255) : branch;
    }
}
