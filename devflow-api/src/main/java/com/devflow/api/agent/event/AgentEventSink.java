package com.devflow.api.agent.event;

@FunctionalInterface
public interface AgentEventSink {
    void record(AgentExecutionEvent event);
}
