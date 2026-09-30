package com.devflow.api.github.integration;

import com.devflow.api.github.config.GithubProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubGitAndPullRequestClientTest {

    private HttpServer server;
    private GithubHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        GithubProperties properties = new GithubProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        client = new GithubHttpClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                properties,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("getRef returns branch reference information and commit SHA")
    void getBranchReturnsDetails() {
        server.createContext("/repos/acme/repo/git/ref/heads/main", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
            byte[] response = """
                    {
                      "ref": "refs/heads/main",
                      "object": {
                        "sha": "base-sha-123"
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        GitRef ref = client.getRef("acme", "repo", "heads/main", "test-token").orElseThrow();
        assertThat(ref.ref()).isEqualTo("refs/heads/main");
        assertThat(ref.sha()).isEqualTo("base-sha-123");
    }

    @Test
    @DisplayName("getCommit returns commit and root tree SHA")
    void getCommitReturnsTreeSha() {
        server.createContext("/repos/acme/repo/git/commits/base-sha-123", exchange -> {
            byte[] response = """
                    {
                      "sha": "base-sha-123",
                      "tree": {
                        "sha": "root-tree-789"
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        GitCommit commit = client.getCommit("acme", "repo", "base-sha-123", "test-token").orElseThrow();
        assertThat(commit.sha()).isEqualTo("base-sha-123");
        assertThat(commit.treeSha()).isEqualTo("root-tree-789");
    }

    @Test
    @DisplayName("createBlob creates Git blob and returns SHA")
    void createBlobSuccess() {
        server.createContext("/repos/acme/repo/git/blobs", exchange -> {
            byte[] response = """
                    {
                      "sha": "blob-sha-abc"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        String blobSha = client.createBlob("acme", "repo", "public class Foo {}", "test-token");
        assertThat(blobSha).isEqualTo("blob-sha-abc");
    }

    @Test
    @DisplayName("createTree creates Git tree and returns SHA")
    void createTreeSuccess() {
        server.createContext("/repos/acme/repo/git/trees", exchange -> {
            byte[] response = """
                    {
                      "sha": "tree-sha-xyz"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        List<GitTreeEntry> entries = List.of(
                new GitTreeEntry("src/Foo.java", "100644", "blob", "blob-sha-abc")
        );
        String treeSha = client.createTree("acme", "repo", "base-tree-sha", entries, "test-token");
        assertThat(treeSha).isEqualTo("tree-sha-xyz");
    }

    @Test
    @DisplayName("createCommit creates Git commit and returns SHA")
    void createCommitSuccess() {
        server.createContext("/repos/acme/repo/git/commits", exchange -> {
            byte[] response = """
                    {
                      "sha": "commit-sha-999",
                      "tree": {
                        "sha": "tree-sha-xyz"
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        String commitSha = client.createCommit("acme", "repo", "Commit message", "tree-sha-xyz", List.of("base-sha-123"), "test-token");
        assertThat(commitSha).isEqualTo("commit-sha-999");
    }

    @Test
    @DisplayName("createRef creates branch ref")
    void createReferenceSuccess() {
        server.createContext("/repos/acme/repo/git/refs", exchange -> {
            byte[] response = """
                    {
                      "ref": "refs/heads/devflow/task-1/change-1",
                      "object": {
                        "sha": "commit-sha-999"
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        GitRef ref = client.createRef("acme", "repo", "refs/heads/devflow/task-1/change-1", "commit-sha-999", "test-token");
        assertThat(ref.ref()).isEqualTo("refs/heads/devflow/task-1/change-1");
        assertThat(ref.sha()).isEqualTo("commit-sha-999");
    }

    @Test
    @DisplayName("createPullRequest creates PR and returns result")
    void createPullRequestSuccess() {
        server.createContext("/repos/acme/repo/pulls", exchange -> {
            byte[] response = """
                    {
                      "number": 42,
                      "html_url": "https://github.com/acme/repo/pull/42",
                      "state": "open",
                      "title": "DevFlow: Fix bug",
                      "body": "Automated PR"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        PullRequestResult result = client.createPullRequest("acme", "repo", "DevFlow: Fix bug", "Automated PR", "devflow/task-1/change-1", "main", "test-token");
        assertThat(result.number()).isEqualTo(42);
        assertThat(result.htmlUrl()).isEqualTo("https://github.com/acme/repo/pull/42");
    }

    @Test
    @DisplayName("findPullRequest finds existing PR for reconciliation")
    void findOpenPullRequestByHeadSuccess() {
        server.createContext("/repos/acme/repo/pulls", exchange -> {
            byte[] response = """
                    [
                      {
                        "number": 42,
                        "html_url": "https://github.com/acme/repo/pull/42",
                        "state": "open",
                        "title": "DevFlow: Fix bug",
                        "body": "Automated PR"
                      }
                    ]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        Optional<PullRequestResult> pr = client.findPullRequest("acme", "repo", "devflow/task-1/change-1", "main", "test-token");
        assertThat(pr).isPresent();
        assertThat(pr.get().number()).isEqualTo(42);
        assertThat(pr.get().htmlUrl()).isEqualTo("https://github.com/acme/repo/pull/42");
    }

    @Test
    @DisplayName("getFileContent retrieves raw file content")
    void getFileContentSuccess() {
        server.createContext("/repos/acme/repo/contents/src/App.java", exchange -> {
            String b64 = java.util.Base64.getEncoder().encodeToString("public class App {}".getBytes(StandardCharsets.UTF_8));
            byte[] response = ("{\"encoding\":\"base64\",\"content\":\"" + b64 + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        String content = client.getFileContent("acme", "repo", "src/App.java", "base-sha", "test-token").orElseThrow();
        assertThat(content).isEqualTo("public class App {}");
    }
}
