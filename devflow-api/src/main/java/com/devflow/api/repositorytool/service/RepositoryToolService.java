package com.devflow.api.repositorytool.service;

import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repositorytool.api.RepositoryFileEntry;
import com.devflow.api.repositorytool.api.RepositoryFileType;
import com.devflow.api.repositorytool.api.RepositoryMetadata;
import com.devflow.api.repositorytool.api.RepositoryReadResult;
import com.devflow.api.repositorytool.api.RepositorySearchMatch;
import com.devflow.api.repositorytool.config.RepositoryToolProperties;
import com.devflow.api.repositorytool.domain.RepositoryWorkspace;
import com.devflow.api.repositorytool.exception.RepositoryToolExceptions;
import com.devflow.api.repositorytool.exception.RepositoryToolException;
import com.devflow.api.repositorytool.security.PathSecurity;
import com.devflow.api.repositorytool.workspace.WorkspaceResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryToolService {

    private final RepositoryRepository repositoryRepository;
    private final OrganizationMemberRepository memberRepository;
    private final RepositoryToolProperties properties;
    private final WorkspaceResolver workspaceResolver;
    private final PathSecurity pathSecurity = new PathSecurity();

    public RepositoryToolService(
            RepositoryRepository repositoryRepository,
            OrganizationMemberRepository memberRepository,
            RepositoryToolProperties properties,
            WorkspaceResolver workspaceResolver) {
        this.repositoryRepository = repositoryRepository;
        this.memberRepository = memberRepository;
        this.properties = properties;
        this.workspaceResolver = workspaceResolver;
    }

    @Transactional(readOnly = true)
    public List<RepositoryFileEntry> listFiles(UUID userId, UUID organizationId, UUID repositoryId,
            String relativeDirectory, int limit) {
        RepositoryWorkspace workspace = authorize(userId, organizationId, repositoryId);
        if (limit <= 0) throw RepositoryToolExceptions.invalidQuery();
        Path directory = pathSecurity.resolve(workspace, relativeDirectory == null ? "" : relativeDirectory,
                properties.getMaxDirectoryDepth(), true);
        if (!Files.isDirectory(directory)) throw RepositoryToolExceptions.fileNotFound();
        List<RepositoryFileEntry> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            List<Path> children = new ArrayList<>();
            stream.forEach(children::add);
            children.sort(Comparator.comparing(path -> relative(workspace, path)));
            int bounded = Math.min(limit, properties.getMaxListResults());
            for (Path child : children) {
                if (result.size() == bounded) break;
                if (!isSafeChild(workspace, child)) continue;
                result.add(new RepositoryFileEntry(relative(workspace, child),
                        Files.isDirectory(child) ? RepositoryFileType.DIRECTORY : RepositoryFileType.FILE,
                        Files.isRegularFile(child) ? Files.size(child) : 0));
            }
            return result;
        } catch (IOException exception) {
            throw RepositoryToolExceptions.workspaceUnavailable();
        }
    }

    @Transactional(readOnly = true)
    public RepositoryReadResult readFile(UUID userId, UUID organizationId, UUID repositoryId, String relativePath) {
        RepositoryWorkspace workspace = authorize(userId, organizationId, repositoryId);
        Path file = pathSecurity.resolve(workspace, relativePath, properties.getMaxDirectoryDepth(), false);
        if (!Files.isRegularFile(file)) throw RepositoryToolExceptions.fileNotFound();
        byte[] bytes = readBounded(file);
        String content = decodeText(bytes);
        return new RepositoryReadResult(relative(workspace, file), content, bytes.length);
    }

    @Transactional(readOnly = true)
    public List<RepositorySearchMatch> search(UUID userId, UUID organizationId, UUID repositoryId,
            String query, String relativeDirectory) {
        RepositoryWorkspace workspace = authorize(userId, organizationId, repositoryId);
        if (query == null || query.isBlank() || query.length() > properties.getMaxSearchQueryCharacters()
                || query.indexOf('\0') >= 0) throw RepositoryToolExceptions.invalidQuery();
        Path directory = pathSecurity.resolve(workspace, relativeDirectory == null ? "" : relativeDirectory,
                properties.getMaxDirectoryDepth(), true);
        if (!Files.isDirectory(directory)) throw RepositoryToolExceptions.fileNotFound();
        List<RepositorySearchMatch> matches = new ArrayList<>();
        searchDirectory(workspace, directory, query, matches, new int[1], 0);
        return matches;
    }

    @Transactional(readOnly = true)
    public RepositoryMetadata metadata(UUID userId, UUID organizationId, UUID repositoryId) {
        authorize(userId, organizationId, repositoryId);
        Repository repository = repositoryRepository.findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(RepositoryToolExceptions::notFound);
        RepositoryWorkspace workspace = workspaceResolver.resolve(repositoryId);
        return new RepositoryMetadata(repositoryId, repository.getFullName(), repository.getProvider().name(),
                repository.getDefaultBranch(), workspace.branch());
    }

    private RepositoryWorkspace authorize(UUID userId, UUID organizationId, UUID repositoryId) {
        if (!properties.isEnabled()) throw RepositoryToolExceptions.disabled();
        OrganizationMember member = memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId)
                .orElseThrow(RepositoryToolExceptions::unauthorized);
        if (member.getOrganization() == null
                || !organizationId.equals(member.getOrganization().getId())) {
            throw RepositoryToolExceptions.unauthorized();
        }
        Repository repository = repositoryRepository.findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(RepositoryToolExceptions::unauthorized);
        if (!repository.isActive()) throw RepositoryToolExceptions.notFound();
        return workspaceResolver.resolve(repositoryId);
    }

    private void searchDirectory(RepositoryWorkspace workspace, Path directory, String query,
            List<RepositorySearchMatch> matches, int[] filesScanned, int depth) {
        if (filesScanned[0] >= properties.getMaxFilesScanned()) throw RepositoryToolExceptions.searchLimit();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            List<Path> children = new ArrayList<>();
            stream.forEach(children::add);
            children.sort(Comparator.comparing(path -> relative(workspace, path)));
            for (Path child : children) {
                if (!isSafeChild(workspace, child)) continue;
                if (Files.isDirectory(child)) {
                    if (depth < properties.getMaxDirectoryDepth()) {
                        searchDirectory(workspace, child, query, matches, filesScanned, depth + 1);
                    }
                } else if (Files.isRegularFile(child)) {
                    if (filesScanned[0] >= properties.getMaxFilesScanned()) {
                        throw RepositoryToolExceptions.searchLimit();
                    }
                    filesScanned[0]++;
                    if (!isSafeChild(workspace, child)) {
                        continue;
                    }
                    searchFile(workspace, child, query, matches);
                }
                if (matches.size() >= properties.getMaxSearchResults()) {
                    throw RepositoryToolExceptions.searchLimit();
                }
            }

        } catch (IOException exception) {
            throw RepositoryToolExceptions.workspaceUnavailable();
        }
    }

    private boolean isSafeChild(RepositoryWorkspace workspace, Path child) {
        if (Files.isSymbolicLink(child)) return false;
        try {
            return child.toRealPath().startsWith(workspace.root());
        } catch (IOException exception) {
            throw RepositoryToolExceptions.workspaceUnavailable();
        }
    }

    private void searchFile(RepositoryWorkspace workspace, Path file, String query,
            List<RepositorySearchMatch> matches) {
        try {
            byte[] bytes = readBounded(file);
            String text = decodeText(bytes);
            String[] lines = text.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(query)) {
                    if (matches.size() >= properties.getMaxSearchResults()) {
                        throw RepositoryToolExceptions.searchLimit();
                    }
                    String snippet = lines[i].length() > properties.getMaxSnippetCharacters()
                            ? lines[i].substring(0, properties.getMaxSnippetCharacters()) : lines[i];
                    matches.add(new RepositorySearchMatch(relative(workspace, file), i + 1, snippet));
                }
            }
        } catch (RepositoryToolException exception) {
            if ("FILE_TOO_LARGE".equals(exception.getCode()) || "BINARY_FILE".equals(exception.getCode())) return;
            throw exception;
        }
    }

    private byte[] readBounded(Path file) {
        try {
            if (Files.size(file) > properties.getMaxFileBytes()) throw RepositoryToolExceptions.fileTooLarge();
            try (InputStream input = Files.newInputStream(file);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[Math.min(8192, properties.getMaxFileBytes())];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > properties.getMaxFileBytes()) throw RepositoryToolExceptions.fileTooLarge();
                }
                return output.toByteArray();
            }
        } catch (RepositoryToolException exception) {
            throw exception;
        } catch (IOException exception) {
            throw RepositoryToolExceptions.workspaceUnavailable();
        }
    }

    private String decodeText(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) throw RepositoryToolExceptions.binaryFile();
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw RepositoryToolExceptions.binaryFile();
        }
    }

    private String relative(RepositoryWorkspace workspace, Path path) {
        return workspace.root().relativize(path).toString().replace('\\', '/');
    }
}
