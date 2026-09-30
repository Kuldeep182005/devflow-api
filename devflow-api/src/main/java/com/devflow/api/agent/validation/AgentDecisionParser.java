package com.devflow.api.agent.validation;

import com.devflow.api.agent.domain.AgentAction;
import com.devflow.api.agent.domain.AgentDecision;
import com.devflow.api.agent.domain.AgentTool;
import com.devflow.api.agent.domain.AgentToolRequest;
import com.devflow.api.agent.exception.InvalidAgentDecisionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentDecisionParser {
    private static final Set<String> ROOT_FIELDS = Set.of("action", "reasoningSummary", "toolRequest", "finalResponse");
    private static final Set<String> TOOL_FIELDS = Set.of("tool", "arguments");
    private final ObjectMapper mapper;

    public AgentDecisionParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AgentDecision parse(String raw, int maxReasoning, int maxFinal) {
        if (raw == null || raw.length() > maxFinal + maxReasoning + 2000) {
            throw new InvalidAgentDecisionException("LLM decision is invalid");
        }
        try {
            JsonNode root = mapper.readTree(raw);
            if (root == null || !root.isObject()) throw invalid();
            root.fieldNames().forEachRemaining(field -> {
                if (!ROOT_FIELDS.contains(field)) throw invalid();
            });
            AgentAction action = enumValue(root, "action", AgentAction.class);
            String reasoning = text(root, "reasoningSummary", false);
            String response = text(root, "finalResponse", false);
            if (reasoning.length() > maxReasoning || response.length() > maxFinal) throw invalid();
            AgentToolRequest request = null;
            JsonNode requestNode = root.get("toolRequest");
            if (action == AgentAction.CALL_TOOL) {
                if (requestNode == null || !requestNode.isObject()) throw invalid();
                requestNode.fieldNames().forEachRemaining(field -> {
                    if (!TOOL_FIELDS.contains(field)) throw invalid();
                });
                AgentTool tool = enumValue(requestNode, "tool", AgentTool.class);
                JsonNode args = requestNode.get("arguments");
                if (args == null || !args.isObject()) throw invalid();
                Map<String, String> arguments = new HashMap<>();
                args.fields().forEachRemaining(entry -> {
                    if (!entry.getValue().isTextual() || arguments.put(entry.getKey(), entry.getValue().textValue()) != null) {
                        throw invalid();
                    }
                });
                request = new AgentToolRequest(tool, Map.copyOf(arguments));
            } else if (requestNode != null && !requestNode.isNull()) {
                throw invalid();
            }
            if (action == AgentAction.FINISH && response.isBlank()) throw invalid();
            if (action != AgentAction.FINISH && !response.isBlank()) throw invalid();
            return new AgentDecision(action, reasoning, request, response);
        } catch (InvalidAgentDecisionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidAgentDecisionException("LLM decision is malformed", exception);
        }
    }

    private String text(JsonNode root, String field, boolean required) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            if (required) throw invalid();
            return "";
        }
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }

    private <T extends Enum<T>> T enumValue(JsonNode root, String field, Class<T> type) {
        String value = text(root, field, true);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidAgentDecisionException("LLM decision contains an unsupported value");
        }
    }

    private InvalidAgentDecisionException invalid() {
        return new InvalidAgentDecisionException("LLM decision is invalid");
    }
}
