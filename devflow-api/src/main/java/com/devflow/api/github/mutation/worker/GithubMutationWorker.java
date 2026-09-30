package com.devflow.api.github.mutation.worker;

import com.devflow.api.github.mutation.service.GithubMutationExecutor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GithubMutationWorker {

    private final GithubMutationExecutor executor;

    public GithubMutationWorker(GithubMutationExecutor executor) {
        this.executor = executor;
    }

    public void process(UUID mutationId) {
        if (mutationId != null) {
            executor.execute(mutationId);
        }
    }
}
