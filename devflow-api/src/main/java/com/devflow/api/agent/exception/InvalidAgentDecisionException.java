package com.devflow.api.agent.exception;

public class InvalidAgentDecisionException extends AgentException {
    public InvalidAgentDecisionException(String message) { super(message); }
    public InvalidAgentDecisionException(String message, Throwable cause) { super(message, cause); }
}
