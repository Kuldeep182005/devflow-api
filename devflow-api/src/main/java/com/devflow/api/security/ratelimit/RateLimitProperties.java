package com.devflow.api.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int loginLimit = 10;
    private int loginWindowSeconds = 60;
    private int registerLimit = 5;
    private int registerWindowSeconds = 60;
    private int webhookLimit = 120;
    private int webhookWindowSeconds = 60;
    private int taskCreateLimit = 30;
    private int taskCreateWindowSeconds = 60;
    private int approvalExecuteLimit = 20;
    private int approvalExecuteWindowSeconds = 60;
    private int globalLimit = 300;
    private int globalWindowSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLoginLimit() {
        return loginLimit;
    }

    public void setLoginLimit(int loginLimit) {
        this.loginLimit = loginLimit;
    }

    public int getLoginWindowSeconds() {
        return loginWindowSeconds;
    }

    public void setLoginWindowSeconds(int loginWindowSeconds) {
        this.loginWindowSeconds = loginWindowSeconds;
    }

    public int getRegisterLimit() {
        return registerLimit;
    }

    public void setRegisterLimit(int registerLimit) {
        this.registerLimit = registerLimit;
    }

    public int getRegisterWindowSeconds() {
        return registerWindowSeconds;
    }

    public void setRegisterWindowSeconds(int registerWindowSeconds) {
        this.registerWindowSeconds = registerWindowSeconds;
    }

    public int getWebhookLimit() {
        return webhookLimit;
    }

    public void setWebhookLimit(int webhookLimit) {
        this.webhookLimit = webhookLimit;
    }

    public int getWebhookWindowSeconds() {
        return webhookWindowSeconds;
    }

    public void setWebhookWindowSeconds(int webhookWindowSeconds) {
        this.webhookWindowSeconds = webhookWindowSeconds;
    }

    public int getTaskCreateLimit() {
        return taskCreateLimit;
    }

    public void setTaskCreateLimit(int taskCreateLimit) {
        this.taskCreateLimit = taskCreateLimit;
    }

    public int getTaskCreateWindowSeconds() {
        return taskCreateWindowSeconds;
    }

    public void setTaskCreateWindowSeconds(int taskCreateWindowSeconds) {
        this.taskCreateWindowSeconds = taskCreateWindowSeconds;
    }

    public int getApprovalExecuteLimit() {
        return approvalExecuteLimit;
    }

    public void setApprovalExecuteLimit(int approvalExecuteLimit) {
        this.approvalExecuteLimit = approvalExecuteLimit;
    }

    public int getApprovalExecuteWindowSeconds() {
        return approvalExecuteWindowSeconds;
    }

    public void setApprovalExecuteWindowSeconds(int approvalExecuteWindowSeconds) {
        this.approvalExecuteWindowSeconds = approvalExecuteWindowSeconds;
    }

    public int getGlobalLimit() {
        return globalLimit;
    }

    public void setGlobalLimit(int globalLimit) {
        this.globalLimit = globalLimit;
    }

    public int getGlobalWindowSeconds() {
        return globalWindowSeconds;
    }

    public void setGlobalWindowSeconds(int globalWindowSeconds) {
        this.globalWindowSeconds = globalWindowSeconds;
    }
}
