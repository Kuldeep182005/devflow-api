package com.devflow.api.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private boolean enabled = false;
    private int maxIterations = 20;
    private int maxToolCalls = 30;
    private int maxContextCharacters = 30_000;
    private int maxToolResultCharacters = 12_000;
    private int maxFinalResponseCharacters = 4_000;
    private int maxReasoningSummaryCharacters = 500;
    private Duration maxExecutionDuration = Duration.ofMinutes(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int value) { maxIterations = value; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int value) { maxToolCalls = value; }
    public int getMaxContextCharacters() { return maxContextCharacters; }
    public void setMaxContextCharacters(int value) { maxContextCharacters = value; }
    public int getMaxToolResultCharacters() { return maxToolResultCharacters; }
    public void setMaxToolResultCharacters(int value) { maxToolResultCharacters = value; }
    public int getMaxFinalResponseCharacters() { return maxFinalResponseCharacters; }
    public void setMaxFinalResponseCharacters(int value) { maxFinalResponseCharacters = value; }
    public int getMaxReasoningSummaryCharacters() { return maxReasoningSummaryCharacters; }
    public void setMaxReasoningSummaryCharacters(int value) { maxReasoningSummaryCharacters = value; }
    public Duration getMaxExecutionDuration() { return maxExecutionDuration; }
    public void setMaxExecutionDuration(Duration value) { maxExecutionDuration = value; }

    public void validate() {
        if (maxIterations <= 0 || maxToolCalls <= 0 || maxContextCharacters <= 0
                || maxToolResultCharacters <= 0 || maxFinalResponseCharacters <= 0
                || maxReasoningSummaryCharacters <= 0 || maxExecutionDuration == null
                || maxExecutionDuration.isZero() || maxExecutionDuration.isNegative()) {
            throw new IllegalStateException("Agent limits are invalid");
        }
    }
}
