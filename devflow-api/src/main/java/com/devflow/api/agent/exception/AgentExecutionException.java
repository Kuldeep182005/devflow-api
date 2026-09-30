package com.devflow.api.agent.exception;

public class AgentExecutionException extends AgentException {
    public AgentExecutionException(String message, Throwable cause) { super(message, cause); }
    public AgentExecutionException(String message) { super(message); }
}
