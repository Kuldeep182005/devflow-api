package com.devflow.api.github.integration;

import com.devflow.api.github.config.GithubProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GithubHttpClient implements GithubApiClient, GithubGitClient, GithubPullRequestClient {

    private final HttpClient httpClient;
    private final GithubProperties properties;
    private final ObjectMapper objectMapper;
    private final URI baseUri;

    public GithubHttpClient(HttpClient githubHttpClient, GithubProperties properties, ObjectMapper objectMapper) {
        this.httpClient = githubHttpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.baseUri = parseBaseUri(properties.getBaseUrl());
    }

    @Override
    public OptionalLong findInstallation(String account, String appJwt) {
        JsonNode installations = request("/app/installations?per_page=100", "GET", appJwt, null);
        if (!installations.isArray()) {
            throw invalidResponse();
        }
        for (JsonNode installation : installations) {
            JsonNode login = installation.path("account").path("login");
            if (login.isTextual() && login.asText().equalsIgnoreCase(account)) {
                long id = installation.path("id").asLong(0);
                if (id > 0) {
                    return OptionalLong.of(id);
                }
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public String createInstallationToken(long installationId, String appJwt) {
        JsonNode token = request("/app/installations/" + installationId + "/access_tokens", "POST", appJwt, null);
        String value = text(token, "token");
        if (value == null || value.isBlank()) {
            throw invalidResponse();
        }
        return value;
    }

    @Override
    public GithubRepository getRepository(String owner, String repository, String installationToken) {
        JsonNode response = request("/repos/" + segment(owner) + "/" + segment(repository),
                "GET", installationToken, null);
        String id = response.path("id").isNumber()
                ? response.path("id").asText()
                : text(response, "node_id");
        String remoteOwner = text(response.path("owner"), "login");
        String fullName = text(response, "full_name");
        String defaultBranch = text(response, "default_branch");
        if (id == null || remoteOwner == null || fullName == null || defaultBranch == null
                || id.isBlank() || remoteOwner.isBlank() || fullName.isBlank() || defaultBranch.isBlank()) {
            throw invalidResponse();
        }
        return new GithubRepository(id, remoteOwner, fullName, defaultBranch);
    }

    @Override
    public Optional<GitRef> getRef(String owner, String repo, String ref, String installationToken) {
        String cleanRef = sanitizeRef(ref);
        Optional<JsonNode> response = requestOptional("/repos/" + segment(owner) + "/" + segment(repo) + "/git/ref/" + cleanRef,
                "GET", installationToken, null);
        if (response.isEmpty()) {
            return Optional.empty();
        }
        JsonNode node = response.get();
        String refName = text(node, "ref");
        String sha = text(node.path("object"), "sha");
        if (refName == null || sha == null) {
            throw invalidResponse();
        }
        return Optional.of(new GitRef(refName, sha));
    }

    @Override
    public Optional<GitCommit> getCommit(String owner, String repo, String commitSha, String installationToken) {
        String sha = segmentSha(commitSha);
        Optional<JsonNode> response = requestOptional("/repos/" + segment(owner) + "/" + segment(repo) + "/git/commits/" + sha,
                "GET", installationToken, null);
        if (response.isEmpty()) {
            return Optional.empty();
        }
        JsonNode node = response.get();
        String commitShaVal = text(node, "sha");
        String treeSha = text(node.path("tree"), "sha");
        if (commitShaVal == null || treeSha == null) {
            throw invalidResponse();
        }
        return Optional.of(new GitCommit(commitShaVal, treeSha));
    }

    @Override
    public Optional<String> getFileContent(String owner, String repo, String path, String ref, String installationToken) {
        String cleanPath = sanitizeFilePath(path);
        String cleanRef = segmentSha(ref);
        Optional<JsonNode> response = requestOptional("/repos/" + segment(owner) + "/" + segment(repo) + "/contents/" + cleanPath + "?ref=" + cleanRef,
                "GET", installationToken, null);
        if (response.isEmpty()) {
            return Optional.empty();
        }
        JsonNode node = response.get();
        String encoding = text(node, "encoding");
        if (!"base64".equalsIgnoreCase(encoding)) {
            throw invalidResponse();
        }
        String content = text(node, "content");
        if (content == null) {
            return Optional.empty();
        }
        String sanitized = content.replaceAll("\\s+", "");
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(sanitized);
            return Optional.of(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw invalidResponse();
        }
    }

    @Override
    public String createBlob(String owner, String repo, String content, String installationToken) {
        try {
            Map<String, String> payload = Map.of("content", content == null ? "" : content, "encoding", "utf-8");
            byte[] body = objectMapper.writeValueAsBytes(payload);
            JsonNode node = request("/repos/" + segment(owner) + "/" + segment(repo) + "/git/blobs",
                    "POST", installationToken, body);
            String sha = text(node, "sha");
            if (sha == null || sha.isBlank()) {
                throw invalidResponse();
            }
            return sha;
        } catch (GithubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw invalidResponse();
        }
    }

    @Override
    public String createTree(String owner, String repo, String baseTreeSha, List<GitTreeEntry> entries, String installationToken) {
        try {
            Map<String, Object> payload = new HashMap<>();
            if (baseTreeSha != null && !baseTreeSha.isBlank()) {
                payload.put("base_tree", segmentSha(baseTreeSha));
            }
            List<Map<String, Object>> treeList = new ArrayList<>();
            for (GitTreeEntry entry : entries) {
                Map<String, Object> item = new HashMap<>();
                item.put("path", sanitizeFilePath(entry.path()));
                item.put("mode", entry.mode());
                item.put("type", entry.type());
                item.put("sha", entry.sha() == null ? null : segmentSha(entry.sha()));
                treeList.add(item);
            }
            payload.put("tree", treeList);
            byte[] body = objectMapper.writeValueAsBytes(payload);
            JsonNode node = request("/repos/" + segment(owner) + "/" + segment(repo) + "/git/trees",
                    "POST", installationToken, body);
            String sha = text(node, "sha");
            if (sha == null || sha.isBlank()) {
                throw invalidResponse();
            }
            return sha;
        } catch (GithubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw invalidResponse();
        }
    }

    @Override
    public String createCommit(String owner, String repo, String message, String treeSha, List<String> parentShas, String installationToken) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", message == null ? "Update" : message);
            payload.put("tree", segmentSha(treeSha));
            payload.put("parents", parentShas.stream().map(this::segmentSha).toList());
            byte[] body = objectMapper.writeValueAsBytes(payload);
            JsonNode node = request("/repos/" + segment(owner) + "/" + segment(repo) + "/git/commits",
                    "POST", installationToken, body);
            String sha = text(node, "sha");
            if (sha == null || sha.isBlank()) {
                throw invalidResponse();
            }
            return sha;
        } catch (GithubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw invalidResponse();
        }
    }

    @Override
    public GitRef createRef(String owner, String repo, String ref, String commitSha, String installationToken) {
        try {
            String fullRef = ref.startsWith("refs/") ? ref : "refs/" + ref;
            Map<String, String> payload = Map.of("ref", fullRef, "sha", segmentSha(commitSha));
            byte[] body = objectMapper.writeValueAsBytes(payload);
            JsonNode node = request("/repos/" + segment(owner) + "/" + segment(repo) + "/git/refs",
                    "POST", installationToken, body);
            String refName = text(node, "ref");
            String sha = text(node.path("object"), "sha");
            if (refName == null || sha == null) {
                throw invalidResponse();
            }
            return new GitRef(refName, sha);
        } catch (GithubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw invalidResponse();
        }
    }

    @Override
    public PullRequestResult createPullRequest(String owner, String repo, String title, String body, String headBranch, String baseBranch, String installationToken) {
        try {
            Map<String, String> payload = Map.of(
                    "title", title,
                    "body", body == null ? "" : body,
                    "head", headBranch,
                    "base", baseBranch
            );
            byte[] reqBody = objectMapper.writeValueAsBytes(payload);
            JsonNode node = request("/repos/" + segment(owner) + "/" + segment(repo) + "/pulls",
                    "POST", installationToken, reqBody);
            long number = node.path("number").asLong(0);
            String htmlUrl = text(node, "html_url");
            String state = text(node, "state");
            if (number <= 0 || htmlUrl == null) {
                throw invalidResponse();
            }
            return new PullRequestResult(number, htmlUrl, state == null ? "open" : state);
        } catch (GithubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw invalidResponse();
        }
    }

    @Override
    public Optional<PullRequestResult> findPullRequest(String owner, String repo, String headBranch, String baseBranch, String installationToken) {
        String query = "?head=" + segment(owner) + ":" + sanitizeRef(headBranch) + "&state=all";
        Optional<JsonNode> response = requestOptional("/repos/" + segment(owner) + "/" + segment(repo) + "/pulls" + query,
                "GET", installationToken, null);
        if (response.isEmpty() || !response.get().isArray() || response.get().isEmpty()) {
            return Optional.empty();
        }
        JsonNode prNode = response.get().get(0);
        long number = prNode.path("number").asLong(0);
        String htmlUrl = text(prNode, "html_url");
        String state = text(prNode, "state");
        if (number <= 0 || htmlUrl == null) {
            return Optional.empty();
        }
        return Optional.of(new PullRequestResult(number, htmlUrl, state == null ? "open" : state));
    }

    private JsonNode request(String path, String method, String bearer, byte[] body) {
        return requestOptional(path, method, bearer, body)
                .orElseThrow(() -> mapStatus(404, path));
    }

    private Optional<JsonNode> requestOptional(String path, String method, String bearer, byte[] body) {
        URI uri = resolve(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "devflow-api");
        if (bearer != null && !bearer.isBlank()) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        } else {
            builder.GET();
        }
        try {
            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] responseBody = readBounded(response.body());
            int status = response.statusCode();
            if (status == 404) {
                return Optional.empty();
            }
            if (status < 200 || status >= 300) {
                throw mapStatus(status, path);
            }
            try {
                JsonNode parsed = objectMapper.readTree(responseBody);
                if (parsed == null) {
                    throw invalidResponse();
                }
                return Optional.of(parsed);
            } catch (Exception exception) {
                if (exception instanceof GithubIntegrationException githubException) {
                    throw githubException;
                }
                throw invalidResponse();
            }
        } catch (GithubIntegrationException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new GithubIntegrationException(HttpStatus.GATEWAY_TIMEOUT, "GITHUB_TIMEOUT",
                    "GitHub request timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GithubIntegrationException(HttpStatus.BAD_GATEWAY, "GITHUB_UNAVAILABLE",
                    "GitHub is unavailable");
        } catch (java.io.IOException | RuntimeException exception) {
            throw new GithubIntegrationException(HttpStatus.BAD_GATEWAY, "GITHUB_UNAVAILABLE",
                    "GitHub is unavailable");
        }
    }

    private byte[] readBounded(InputStream stream) throws java.io.IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int limit = properties.getMaxResponseBytes();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new GithubIntegrationException(HttpStatus.BAD_GATEWAY, "GITHUB_RESPONSE_TOO_LARGE",
                            "GitHub returned an oversized response");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private GithubIntegrationException mapStatus(int status, String path) {
        if (status == 401 || status == 403) {
            return new GithubIntegrationException(HttpStatus.BAD_GATEWAY,
                    "GITHUB_AUTHENTICATION_FAILED", "GitHub authentication failed");
        }
        if (status == 404) {
            String code = path.startsWith("/repos/")
                    ? "GITHUB_REPOSITORY_NOT_FOUND" : "GITHUB_RESOURCE_NOT_FOUND";
            return new GithubIntegrationException(HttpStatus.NOT_FOUND, code, "The GitHub resource was not found");
        }
        if (status == 408 || status == 429 || status >= 500) {
            return new GithubIntegrationException(HttpStatus.BAD_GATEWAY,
                    "GITHUB_UNAVAILABLE", "GitHub is temporarily unavailable");
        }
        if (status == 409) {
            return new GithubIntegrationException(HttpStatus.CONFLICT,
                    "GITHUB_CONFLICT", "GitHub rejected the request with a conflict");
        }
        return new GithubIntegrationException(HttpStatus.BAD_GATEWAY,
                "GITHUB_REQUEST_FAILED", "GitHub rejected the request");
    }

    private URI parseBaseUri(String configured) {
        try {
            URI uri = URI.create(configured == null ? "" : configured.trim());
            if (uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            String value = uri.toString();
            return URI.create(value.endsWith("/") ? value : value + "/");
        } catch (RuntimeException exception) {
            throw new IllegalStateException("github.app.base-url must be an absolute HTTP(S) URL");
        }
    }

    private URI resolve(String path) {
        return baseUri.resolve(path.startsWith("/") ? path.substring(1) : path);
    }

    private String segment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new GithubIntegrationException(HttpStatus.BAD_REQUEST, "INVALID_REPOSITORY",
                    "The repository identifier is invalid");
        }
        return value;
    }

    private String segmentSha(String sha) {
        if (sha == null || !sha.matches("[a-fA-F0-9]{4,64}|[A-Za-z0-9._-]+")) {
            throw new GithubIntegrationException(HttpStatus.BAD_REQUEST, "INVALID_COMMIT_SHA", "Invalid commit SHA");
        }
        return sha;
    }

    private String sanitizeRef(String ref) {
        if (ref == null || ref.isBlank() || ref.contains("..") || ref.contains("\\") || !ref.matches("[A-Za-z0-9_./-]+")) {
            throw new GithubIntegrationException(HttpStatus.BAD_REQUEST, "INVALID_GIT_REF", "Invalid git reference");
        }
        return ref.startsWith("/") ? ref.substring(1) : ref;
    }

    private String sanitizeFilePath(String path) {
        if (path == null || path.isBlank() || path.contains("..") || path.contains("\\")) {
            throw new GithubIntegrationException(HttpStatus.BAD_REQUEST, "INVALID_FILE_PATH", "Invalid file path");
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private Duration timeout() {
        return properties.getReadTimeout();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private GithubIntegrationException invalidResponse() {
        return new GithubIntegrationException(HttpStatus.BAD_GATEWAY, "GITHUB_INVALID_RESPONSE",
                "GitHub returned an invalid response");
    }
}
