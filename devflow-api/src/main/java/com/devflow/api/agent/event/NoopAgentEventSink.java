package com.devflow.api.agent.event;

import org.springframework.stereotype.Component;

@Component
public class NoopAgentEventSink implements AgentEventSink {
    @Override
    public void record(AgentExecutionEvent event) {
    }
}
