package com.devflow.api.agent.domain;

import java.util.Map;

public record AgentToolRequest(AgentTool tool, Map<String, String> arguments) {
}
