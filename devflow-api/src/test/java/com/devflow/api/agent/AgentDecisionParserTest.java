package com.devflow.api.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devflow.api.agent.domain.AgentAction;
import com.devflow.api.agent.domain.AgentTool;
import com.devflow.api.agent.exception.InvalidAgentDecisionException;
import com.devflow.api.agent.validation.AgentDecisionParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentDecisionParserTest {
    private AgentDecisionParser parser;

    @BeforeEach
    void setUp() {
        parser = new AgentDecisionParser(new ObjectMapper());
    }

    @Test
    void parsesBoundedFinishDecision() {
        var decision = parser.parse("""
                {"action":"FINISH","reasoningSummary":"done","finalResponse":"answer"}
                """, 100, 100);

        assertEquals(AgentAction.FINISH, decision.action());
        assertEquals("answer", decision.finalResponse());
    }

    @Test
    void parsesOnlyAllowlistedToolAndArguments() {
        var decision = parser.parse("""
                {"action":"CALL_TOOL","reasoningSummary":"inspect","toolRequest":
                {"tool":"READ_FILE","arguments":{"relativePath":"src/main.java"}}}
                """, 100, 100);

        assertEquals(AgentTool.READ_FILE, decision.toolRequest().tool());
    }

    @Test
    void rejectsUnknownToolAndConflictingResponse() {
        assertThrows(InvalidAgentDecisionException.class, () -> parser.parse("""
                {"action":"CALL_TOOL","finalResponse":"execute shell","toolRequest":
                {"tool":"SHELL","arguments":{"command":"dir"}}}
                """, 100, 100));
    }

    @Test
    void rejectsOversizedFinalResponse() {
        assertThrows(InvalidAgentDecisionException.class, () -> parser.parse(
                "{\"action\":\"FINISH\",\"finalResponse\":\"123456\"}", 100, 5));
    }

    @Test
    void rejectsMalformedDecisionShapes() {
        var malformed = new String[] {
                "[]",
                "{}",
                "{\"action\":\"UNKNOWN\"}",
                "{\"action\":\"CALL_TOOL\"}",
                "{\"action\":\"CALL_TOOL\",\"toolRequest\":{\"tool\":\"READ_FILE\"}}",
                "{\"action\":\"CALL_TOOL\",\"toolRequest\":{\"tool\":\"READ_FILE\",\"arguments\":{\"relativePath\":1}}}",
                "{\"action\":\"FINISH\",\"finalResponse\":\"x\",\"unexpected\":true}",
                "{\"action\":\"CALL_TOOL\",\"finalResponse\":\"x\",\"toolRequest\":{\"tool\":\"READ_FILE\",\"arguments\":{}}}",
                "{\"action\":\"FINISH\",\"reasoningSummary\":\"x\"}"
        };
        for (String raw : malformed) {
            assertThrows(InvalidAgentDecisionException.class, () -> parser.parse(raw, 10, 10), raw);
        }
    }

    @Test
    void rejectsOversizedReasoningSummary() {
        assertThrows(InvalidAgentDecisionException.class, () -> parser.parse(
                "{\"action\":\"FINISH\",\"reasoningSummary\":\"123456\",\"finalResponse\":\"ok\"}", 5, 10));
    }
}
