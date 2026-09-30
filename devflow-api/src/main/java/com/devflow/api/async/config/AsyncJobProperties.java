package com.devflow.api.async.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "async")
public class AsyncJobProperties {

    private final Outbox outbox = new Outbox();
    private final Worker worker = new Worker();

    public Outbox getOutbox() {
        return outbox;
    }

    public Worker getWorker() {
        return worker;
    }

    public static class Outbox {
        private long pollIntervalMs = 1000;
        private int batchSize = 25;
        private long claimTimeoutSeconds = 60;
        private long retryDelayMs = 1000;

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long value) { pollIntervalMs = value; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int value) { batchSize = value; }
        public long getClaimTimeoutSeconds() { return claimTimeoutSeconds; }
        public void setClaimTimeoutSeconds(long value) { claimTimeoutSeconds = value; }
        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long value) { retryDelayMs = value; }
    }

    public static class Worker {
        private boolean enabled = true;
        private String stream = "devflow:jobs";
        private String consumerGroup = "devflow-workers";
        private String consumerName = "devflow-worker";
        private int concurrency = 4;
        private int batchSize = 25;
        private long blockTimeoutMs = 1000;
        private long pendingIdleMs = 30000;
        private long jobTimeoutMs = 30000;
        private int maxAttempts = 5;
        private long retryBaseDelayMs = 1000;
        private long shutdownTimeoutMs = 10000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getStream() { return stream; }
        public void setStream(String value) { stream = value; }
        public String getConsumerGroup() { return consumerGroup; }
        public void setConsumerGroup(String value) { consumerGroup = value; }
        public String getConsumerName() { return consumerName; }
        public void setConsumerName(String value) { consumerName = value; }
        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int value) { concurrency = value; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int value) { batchSize = value; }
        public long getBlockTimeoutMs() { return blockTimeoutMs; }
        public void setBlockTimeoutMs(long value) { blockTimeoutMs = value; }
        public long getPendingIdleMs() { return pendingIdleMs; }
        public void setPendingIdleMs(long value) { pendingIdleMs = value; }
        public long getJobTimeoutMs() { return jobTimeoutMs; }
        public void setJobTimeoutMs(long value) { jobTimeoutMs = value; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int value) { maxAttempts = value; }
        public long getRetryBaseDelayMs() { return retryBaseDelayMs; }
        public void setRetryBaseDelayMs(long value) { retryBaseDelayMs = value; }
        public long getShutdownTimeoutMs() { return shutdownTimeoutMs; }
        public void setShutdownTimeoutMs(long value) { shutdownTimeoutMs = value; }
    }
}
