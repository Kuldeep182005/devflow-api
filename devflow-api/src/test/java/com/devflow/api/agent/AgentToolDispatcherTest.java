package com.devflow.api.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devflow.api.agent.domain.AgentExecutionContext;
import com.devflow.api.agent.domain.AgentTool;
import com.devflow.api.agent.domain.AgentToolRequest;
import com.devflow.api.agent.domain.AgentToolResult;
import com.devflow.api.agent.exception.InvalidAgentDecisionException;
import com.devflow.api.agent.tool.AgentToolDispatcher;
import com.devflow.api.repositorytool.api.RepositoryFileEntry;
import com.devflow.api.repositorytool.api.RepositoryFileType;
import com.devflow.api.repositorytool.api.RepositoryMetadata;
import com.devflow.api.repositorytool.api.RepositoryReadResult;
import com.devflow.api.repositorytool.api.RepositorySearchMatch;
import com.devflow.api.repositorytool.service.RepositoryToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class AgentToolDispatcherTest {
    private final AgentExecutionContext context = new AgentExecutionContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "objective", "repo");
    private RepositoryToolService repositoryTools;
    private AgentToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        repositoryTools = mock(RepositoryToolService.class);
        dispatcher = new AgentToolDispatcher(repositoryTools, new ObjectMapper());
    }

    @Test
    void dispatchesEveryAllowlistedTool() {
        when(repositoryTools.listFiles(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new RepositoryFileEntry("a.txt", RepositoryFileType.FILE, 1)));
        when(repositoryTools.readFile(any(), any(), any(), any()))
                .thenReturn(new RepositoryReadResult("a.txt", "x", 1));
        when(repositoryTools.search(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new RepositorySearchMatch("a.txt", 1, "x")));
        when(repositoryTools.metadata(any(), any(), any()))
                .thenReturn(new RepositoryMetadata(context.repositoryId(), "repo", "GITHUB", "main", "main"));

        assertEquals("LIST_FILES", dispatcher.dispatch(context,
                new AgentToolRequest(AgentTool.LIST_FILES, Map.of())).tool());
        assertEquals("READ_FILE", dispatcher.dispatch(context,
                new AgentToolRequest(AgentTool.READ_FILE, Map.of("relativePath", "a.txt"))).tool());
        assertEquals("SEARCH_FILES", dispatcher.dispatch(context,
                new AgentToolRequest(AgentTool.SEARCH_FILES, Map.of("query", "x"))).tool());
        assertEquals("GET_REPOSITORY_METADATA", dispatcher.dispatch(context,
                new AgentToolRequest(AgentTool.GET_REPOSITORY_METADATA, Map.of())).tool());
        verify(repositoryTools).listFiles(any(), any(), any(), any(), anyInt());
        verify(repositoryTools).readFile(any(), any(), any(), any());
        verify(repositoryTools).search(any(), any(), any(), any(), any());
        verify(repositoryTools).metadata(any(), any(), any());
    }

    @Test
    void rejectsUnsafePathsBeforeRepositoryAccess() {
        for (String path : List.of("../secret", "..\\secret", "/etc/passwd",
                "C:\\secret", "\\\\server\\share\\secret", "safe\0file")) {
            assertThrows(InvalidAgentDecisionException.class, () -> dispatcher.dispatch(context,
                    new AgentToolRequest(AgentTool.READ_FILE, Map.of("relativePath", path))));
        }
        verifyNoInteractions(repositoryTools);
    }

    @Test
    void rejectsUnexpectedAndMissingArguments() {
        assertThrows(InvalidAgentDecisionException.class, () -> dispatcher.dispatch(context,
                new AgentToolRequest(AgentTool.LIST_FILES, Map.of("relativePath", "x"))));
        assertThrows(InvalidAgentDecisionException.class, () -> dispatcher.dispatch(context,
                new AgentToolRequest(AgentTool.SEARCH_FILES, Map.of())));
        assertThrows(InvalidAgentDecisionException.class, () -> dispatcher.dispatch(context,
                new AgentToolRequest(AgentTool.GET_REPOSITORY_METADATA, Map.of("x", "y"))));
        verifyNoInteractions(repositoryTools);
    }

    @Test
    void rejectsOversizedSearchAndUnsafeSearchDirectory() {
        assertThrows(InvalidAgentDecisionException.class, () -> dispatcher.dispatch(context,
                new AgentToolRequest(AgentTool.SEARCH_FILES, Map.of("query", "x".repeat(513)))));
        assertThrows(InvalidAgentDecisionException.class, () -> dispatcher.dispatch(context,
                new AgentToolRequest(AgentTool.SEARCH_FILES,
                        Map.of("query", "x", "relativeDirectory", "../secret"))));
        verifyNoInteractions(repositoryTools);
    }
}
