package com.devflow.api.github.integration;

import com.devflow.api.github.config.GithubProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubHttpClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verifiesRepositoryAgainstConfiguredMockServerOnly() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/acme/api", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .isEqualTo("Bearer installation-token");
            byte[] response = """
                    {"id":42,"owner":{"login":"acme"},"full_name":"acme/api","default_branch":"main"}
                    """.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        GithubProperties properties = new GithubProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        GithubHttpClient client = new GithubHttpClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                properties,
                new ObjectMapper());

        GithubRepository repository = client.getRepository("acme", "api", "installation-token");

        assertThat(repository.id()).isEqualTo("42");
        assertThat(repository.fullName()).isEqualTo("acme/api");
    }

    @Test
    void rejectsUrlShapedRepositorySegments() {
        GithubProperties properties = new GithubProperties();
        properties.setBaseUrl("https://api.github.com");
        GithubHttpClient client = new GithubHttpClient(
                HttpClient.newHttpClient(), properties, new ObjectMapper());

        assertThatThrownBy(() -> client.getRepository("https://attacker.example", "api", "token"))
                .isInstanceOf(GithubIntegrationException.class)
                .hasMessage("The repository identifier is invalid");
    }
}
