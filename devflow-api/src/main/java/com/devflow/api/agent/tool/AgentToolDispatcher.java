package com.devflow.api.agent.tool;

import com.devflow.api.agent.domain.AgentExecutionContext;
import com.devflow.api.agent.domain.AgentTool;
import com.devflow.api.agent.domain.AgentToolRequest;
import com.devflow.api.agent.domain.AgentToolResult;
import com.devflow.api.agent.exception.InvalidAgentDecisionException;
import com.devflow.api.repositorytool.api.RepositoryFileEntry;
import com.devflow.api.repositorytool.api.RepositoryMetadata;
import com.devflow.api.repositorytool.api.RepositoryReadResult;
import com.devflow.api.repositorytool.api.RepositorySearchMatch;
import com.devflow.api.repositorytool.service.RepositoryToolService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AgentToolDispatcher {
    private static final int MAX_ARGUMENT_CHARS = 512;
    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("^[A-Za-z]:([\\\\/].*)?$");
    private final RepositoryToolService repositoryTools;
    private final ObjectMapper mapper;

    public AgentToolDispatcher(RepositoryToolService repositoryTools, ObjectMapper mapper) {
        this.repositoryTools = repositoryTools;
        this.mapper = mapper;
    }

    public AgentToolResult dispatch(AgentExecutionContext context, AgentToolRequest request) {
        if (request == null || request.tool() == null || request.arguments() == null) {
            throw new InvalidAgentDecisionException("Tool request is invalid");
        }
        request.arguments().forEach((key, value) -> {
            if (key == null || key.length() > 80 || value == null || value.length() > MAX_ARGUMENT_CHARS) {
                throw new InvalidAgentDecisionException("Tool arguments are invalid");
            }
        });
        try {
            return switch (request.tool()) {
                case LIST_FILES -> list(context, request.arguments());
                case READ_FILE -> read(context, request.arguments());
                case SEARCH_FILES -> search(context, request.arguments());
                case GET_REPOSITORY_METADATA -> metadata(context, request.arguments());
            };
        } catch (com.devflow.api.repositorytool.exception.RepositoryToolException exception) {
            throw new InvalidAgentDecisionException("Repository tool request failed", exception);
        }
    }

    private AgentToolResult list(AgentExecutionContext context, Map<String, String> arguments) {
        rejectUnexpected(arguments, "relativeDirectory");
        String directory = arguments.getOrDefault("relativeDirectory", "");
        validateRelativePath(directory);
        List<RepositoryFileEntry> result = repositoryTools.listFiles(
                context.userId(), context.organizationId(), context.repositoryId(), directory, 100);
        return result("LIST_FILES", result);
    }

    private AgentToolResult read(AgentExecutionContext context, Map<String, String> arguments) {
        rejectRequired(arguments, "relativePath");
        validateRelativePath(arguments.get("relativePath"));
        RepositoryReadResult result = repositoryTools.readFile(context.userId(), context.organizationId(),
                context.repositoryId(), arguments.get("relativePath"));
        return result("READ_FILE", result);
    }

    private AgentToolResult search(AgentExecutionContext context, Map<String, String> arguments) {
        rejectUnexpected(arguments, "query", "relativeDirectory");
        String query = required(arguments, "query");
        validateRelativePath(arguments.getOrDefault("relativeDirectory", ""));
        List<RepositorySearchMatch> result = repositoryTools.search(context.userId(), context.organizationId(),
                context.repositoryId(), query, arguments.get("relativeDirectory"));
        return result("SEARCH_FILES", result);
    }

    private AgentToolResult metadata(AgentExecutionContext context, Map<String, String> arguments) {
        if (!arguments.isEmpty()) throw new InvalidAgentDecisionException("Metadata arguments are not allowed");
        RepositoryMetadata result = repositoryTools.metadata(context.userId(), context.organizationId(),
                context.repositoryId());
        return result("GET_REPOSITORY_METADATA", result);
    }

    private AgentToolResult result(String tool, Object value) {
        try {
            String data = mapper.writeValueAsString(value);
            return new AgentToolResult(tool, "Bounded repository tool result", data);
        } catch (JsonProcessingException exception) {
            throw new InvalidAgentDecisionException("Tool result could not be encoded", exception);
        }
    }

    private String required(Map<String, String> arguments, String name) {
        String value = arguments.get(name);
        if (value == null || value.isBlank()) throw new InvalidAgentDecisionException("Required tool argument is missing");
        return value;
    }

    private void validateRelativePath(String path) {
        if (path.indexOf('\0') >= 0 || path.startsWith("/") || path.startsWith("\\\\")
                || WINDOWS_DRIVE_PATH.matcher(path).matches()) {
            throw new InvalidAgentDecisionException("Tool path is invalid");
        }
        String[] segments = path.replace('\\', '/').split("/");
        for (String segment : segments) {
            if ("..".equals(segment)) throw new InvalidAgentDecisionException("Tool path is invalid");
        }
    }

    private void rejectRequired(Map<String, String> arguments, String name) {
        rejectUnexpected(arguments, name);
        required(arguments, name);
    }

    private void rejectUnexpected(Map<String, String> arguments, String... allowed) {
        List<String> names = List.of(allowed);
        if (arguments.keySet().stream().anyMatch(key -> !names.contains(key))) {
            throw new InvalidAgentDecisionException("Unsupported tool argument");
        }
    }
}
