package com.devflow.api.github.integration;

public record PullRequestResult(long number, String htmlUrl, String state) {
}
