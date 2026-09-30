package com.devflow.api.github.mutation.exception;

import com.devflow.api.web.ApiException;
import org.springframework.http.HttpStatus;

public class GithubMutationException extends ApiException {

    public GithubMutationException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
