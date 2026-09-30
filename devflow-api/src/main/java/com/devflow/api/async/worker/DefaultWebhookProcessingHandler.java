package com.devflow.api.async.worker;

import com.devflow.api.async.job.JobEnvelope;
import com.devflow.api.async.job.JobType;
import com.devflow.api.github.mutation.worker.GithubMutationWorker;
import com.devflow.api.sandbox.service.SandboxExecutionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Dispatches async jobs by JobType.
 */
@Component
public class DefaultWebhookProcessingHandler implements WebhookProcessingHandler {

    private final ObjectProvider<SandboxExecutionService> sandboxExecutionServiceProvider;
    private final ObjectProvider<GithubMutationWorker> githubMutationWorkerProvider;

    public DefaultWebhookProcessingHandler(
            ObjectProvider<SandboxExecutionService> sandboxExecutionServiceProvider,
            ObjectProvider<GithubMutationWorker> githubMutationWorkerProvider) {
        this.sandboxExecutionServiceProvider = sandboxExecutionServiceProvider;
        this.githubMutationWorkerProvider = githubMutationWorkerProvider;
    }

    @Override
    public void process(JobEnvelope job) {
        if (job != null && job.type() == JobType.SANDBOX_EXECUTION) {
            SandboxExecutionService service = sandboxExecutionServiceProvider.getIfAvailable();
            if (service != null && job.eventId() != null) {
                service.executeExecution(job.eventId());
            }
        } else if (job != null && job.type() == JobType.GITHUB_PR_CREATION) {
            GithubMutationWorker worker = githubMutationWorkerProvider.getIfAvailable();
            if (worker != null && job.eventId() != null) {
                worker.process(job.eventId());
            }
        }
        // Webhook business processing is handled by a later batch.
    }
}
