package com.devflow.api.async.worker;

import com.devflow.api.async.job.JobEnvelope;

@FunctionalInterface
public interface WebhookProcessingHandler {

    void process(JobEnvelope job) throws Exception;
}
