package com.devflow.api.github.integration;

public record GitTreeEntry(String path, String mode, String type, String sha) {
}
