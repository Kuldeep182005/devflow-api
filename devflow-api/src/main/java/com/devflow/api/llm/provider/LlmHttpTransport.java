package com.devflow.api.llm.provider;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@FunctionalInterface
public interface LlmHttpTransport {
    HttpResponse<InputStream> send(HttpRequest request) throws java.io.IOException, InterruptedException;
}
