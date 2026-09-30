package com.devflow.api.agent.service;

import com.devflow.api.agent.config.AgentProperties;
import com.devflow.api.agent.domain.AgentAction;
import com.devflow.api.agent.domain.AgentDecision;
import com.devflow.api.agent.domain.AgentExecutionContext;
import com.devflow.api.agent.domain.AgentExecutionResult;
import com.devflow.api.agent.domain.AgentState;
import com.devflow.api.agent.domain.AgentToolResult;
import com.devflow.api.agent.event.AgentEventSink;
import com.devflow.api.agent.event.AgentEventType;
import com.devflow.api.agent.event.AgentExecutionEvent;
import com.devflow.api.agent.exception.AgentCancelledException;
import com.devflow.api.agent.exception.AgentExecutionException;
import com.devflow.api.agent.exception.AgentLimitExceededException;
import com.devflow.api.agent.tool.AgentToolDispatcher;
import com.devflow.api.agent.validation.AgentDecisionParser;
import com.devflow.api.agent.validation.AgentStateMachine;
import com.devflow.api.llm.api.LlmRequest;
import com.devflow.api.llm.api.LlmResponse;
import com.devflow.api.llm.service.LlmService;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.observability.evaluation.AiEvaluationService;
import com.devflow.api.observability.metrics.DevFlowMetrics;
import com.devflow.api.task.domain.TaskStatus;
import java.time.Duration;
import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentExecutionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentExecutionService.class);
    private final AgentProperties properties;
    private final AgentTaskContextLoader contextLoader;
    private final AgentPromptBuilder promptBuilder;
    private final AgentDecisionParser decisionParser;
    private final AgentStateMachine stateMachine;
    private final AgentToolDispatcher dispatcher;
    private final LlmService llmService;
    private final TaskRepository taskRepository;
    private final AgentEventSink eventSink;
    private final DevFlowMetrics metrics;
    private final AiEvaluationService evaluationService;

    public AgentExecutionService(AgentProperties properties, AgentTaskContextLoader contextLoader,
            AgentPromptBuilder promptBuilder, AgentDecisionParser decisionParser, AgentStateMachine stateMachine,
            AgentToolDispatcher dispatcher, LlmService llmService, TaskRepository taskRepository,
            AgentEventSink eventSink) {
        this(properties, contextLoader, promptBuilder, decisionParser, stateMachine, dispatcher, llmService,
                taskRepository, eventSink, (DevFlowMetrics) null, (AiEvaluationService) null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AgentExecutionService(AgentProperties properties, AgentTaskContextLoader contextLoader,
            AgentPromptBuilder promptBuilder, AgentDecisionParser decisionParser, AgentStateMachine stateMachine,
            AgentToolDispatcher dispatcher, LlmService llmService, TaskRepository taskRepository,
            AgentEventSink eventSink,
            org.springframework.beans.factory.ObjectProvider<DevFlowMetrics> metricsProvider,
            org.springframework.beans.factory.ObjectProvider<AiEvaluationService> evaluationServiceProvider) {
        this(properties, contextLoader, promptBuilder, decisionParser, stateMachine, dispatcher, llmService,
                taskRepository, eventSink,
                metricsProvider != null ? metricsProvider.getIfAvailable() : null,
                evaluationServiceProvider != null ? evaluationServiceProvider.getIfAvailable() : null);
    }

    public AgentExecutionService(AgentProperties properties, AgentTaskContextLoader contextLoader,
            AgentPromptBuilder promptBuilder, AgentDecisionParser decisionParser, AgentStateMachine stateMachine,
            AgentToolDispatcher dispatcher, LlmService llmService, TaskRepository taskRepository,
            AgentEventSink eventSink,
            DevFlowMetrics metrics,
            AiEvaluationService evaluationService) {
        this.properties = properties;
        this.contextLoader = contextLoader;
        this.promptBuilder = promptBuilder;
        this.decisionParser = decisionParser;
        this.stateMachine = stateMachine;
        this.dispatcher = dispatcher;
        this.llmService = llmService;
        this.taskRepository = taskRepository;
        this.eventSink = eventSink;
        this.metrics = metrics;
        this.evaluationService = evaluationService;
        properties.validate();
    }


    public AgentExecutionResult execute(UUID userId, UUID organizationId, UUID taskId) {
        return execute(userId, organizationId, taskId, () -> false);
    }

    public AgentExecutionResult execute(UUID userId, UUID organizationId, UUID taskId, BooleanSupplier cancelled) {
        UUID executionId = UUID.randomUUID();
        event(executionId, taskId, AgentEventType.AGENT_STARTED, "Agent execution started");
        if (!properties.isEnabled()) {
            event(executionId, taskId, AgentEventType.AGENT_FAILED, "Agent execution failed");
            return new AgentExecutionResult(executionId, AgentState.FAILED, "", 0, 0);
        }
        AgentExecutionContext context = contextLoader.load(userId, organizationId, taskId);
        if (evaluationService != null) {
            evaluationService.recordExecutionStart(context.organizationId(), context.taskId(), executionId, "openai-compatible", "default");
        }
        Instant deadline = Instant.now().plus(properties.getMaxExecutionDuration());
        AgentState state = AgentState.NEW;
        int iterations = 0;
        int toolCalls = 0;
        List<AgentToolResult> results = new ArrayList<>();
        try {
            stateMachine.require(state, AgentState.RUNNING);
            state = AgentState.RUNNING;
            for (int i = 0; i < properties.getMaxIterations(); i++) {
                check(cancelled, deadline, taskId, iterations, toolCalls);
                iterations++;
                stateMachine.require(state, AgentState.WAITING_FOR_LLM);
                state = AgentState.WAITING_FOR_LLM;
                String prompt = promptBuilder.build(context, results, iterations,
                        properties.getMaxToolCalls() - toolCalls);
                if (prompt.length() > properties.getMaxContextCharacters()) {
                    throw new AgentLimitExceededException("Agent context limit reached");
                }
                event(executionId, taskId, AgentEventType.LLM_REQUESTED, "LLM decision requested");
                LlmResponse response = llmService.complete(new LlmRequest(prompt, 0.0, 1024));
                AgentDecision decision = decisionParser.parse(response.text(),
                        properties.getMaxReasoningSummaryCharacters(),
                        properties.getMaxFinalResponseCharacters());
                event(executionId, taskId, AgentEventType.LLM_DECISION_ACCEPTED, "LLM decision accepted");
                if (decision.action() == AgentAction.FINISH) {
                    stateMachine.require(state, AgentState.SUCCEEDED);
                    state = AgentState.SUCCEEDED;
                    event(executionId, taskId, AgentEventType.AGENT_COMPLETED, "Agent completed");
                    if (evaluationService != null) {
                        evaluationService.recordExecutionComplete(executionId, iterations, toolCalls, state.name(), null);
                    }
                    if (metrics != null) {
                        metrics.recordAiAgentCompletion(state.name());
                    }
                    return new AgentExecutionResult(executionId, state, decision.finalResponse(), iterations, toolCalls);
                }
                if (decision.action() != AgentAction.CALL_TOOL) {
                    throw new AgentExecutionException("Agent decision did not make progress");
                }
                if (toolCalls >= properties.getMaxToolCalls()) {
                    throw new AgentLimitExceededException("Agent tool-call limit reached");
                }
                stateMachine.require(state, AgentState.WAITING_FOR_TOOL);
                state = AgentState.WAITING_FOR_TOOL;
                check(cancelled, deadline, taskId, iterations, toolCalls);
                toolCalls++;
                event(executionId, taskId, AgentEventType.TOOL_REQUESTED, "Repository tool requested");
                Instant toolStart = Instant.now();
                AgentToolResult result = dispatcher.dispatch(context, decision.toolRequest());
                if (metrics != null) {
                    metrics.recordRepoToolCall(decision.toolRequest().tool() != null ? decision.toolRequest().tool().name() : "unknown",
                            "SUCCESS", Duration.between(toolStart, Instant.now()));
                }
                results.add(boundResult(result));
                event(executionId, taskId, AgentEventType.TOOL_SUCCEEDED, "Repository tool completed");
                if (toolCalls >= properties.getMaxToolCalls()) {
                    throw new AgentLimitExceededException("Agent tool-call limit reached");
                }
            }
            throw new AgentLimitExceededException("Agent iteration limit reached");
        } catch (AgentCancelledException exception) {
            state = transitionTerminal(state, AgentState.CANCELLED);
            event(executionId, taskId, AgentEventType.AGENT_CANCELLED, "Agent cancelled");
            if (evaluationService != null) {
                evaluationService.recordExecutionComplete(executionId, iterations, toolCalls, state.name(), "CANCELLED");
            }
            if (metrics != null) {
                metrics.recordAiAgentCompletion(state.name());
            }
            return new AgentExecutionResult(executionId, state, "", iterations, toolCalls);
        } catch (AgentLimitExceededException exception) {
            state = transitionTerminal(state, AgentState.LIMIT_REACHED);
            event(executionId, taskId, AgentEventType.AGENT_LIMIT_REACHED, "Agent limit reached");
            if (evaluationService != null) {
                evaluationService.recordExecutionComplete(executionId, iterations, toolCalls, state.name(), "LIMIT_REACHED");
            }
            if (metrics != null) {
                metrics.recordAiAgentCompletion(state.name());
            }
            return new AgentExecutionResult(executionId, state, "", iterations, toolCalls);
        } catch (Exception exception) {
            state = transitionTerminal(state, AgentState.FAILED);
            event(executionId, taskId, AgentEventType.AGENT_FAILED, "Agent execution failed");
            LOGGER.warn("Agent execution failed taskId={} executionId={}", taskId, executionId);
            if (evaluationService != null) {
                evaluationService.recordExecutionComplete(executionId, iterations, toolCalls, state.name(), "EXECUTION_FAILED");
            }
            if (metrics != null) {
                metrics.recordAiAgentCompletion(state.name());
            }
            return new AgentExecutionResult(executionId, state, "", iterations, toolCalls);
        }

    }

    private AgentToolResult boundResult(AgentToolResult result) {
        String data = result.data();
        if (data.length() <= properties.getMaxToolResultCharacters()) return result;
        return new AgentToolResult(result.tool(), result.summary(),
                data.substring(0, properties.getMaxToolResultCharacters()));
    }

    private void check(BooleanSupplier cancelled, Instant deadline, UUID taskId, int iterations, int toolCalls) {
        if (cancelled.getAsBoolean() || taskCancelled(taskId)) throw new AgentCancelledException("Agent was cancelled");
        if (Instant.now().isAfter(deadline)) throw new AgentLimitExceededException("Agent execution deadline reached");
        if (iterations >= properties.getMaxIterations() || toolCalls > properties.getMaxToolCalls()) {
            throw new AgentLimitExceededException("Agent execution limit reached");
        }
    }

    private boolean taskCancelled(UUID taskId) {
        return taskRepository.findById(taskId).map(task -> task.getStatus() == TaskStatus.CANCELLED).orElse(false);
    }

    private AgentState transitionTerminal(AgentState current, AgentState terminal) {
        stateMachine.require(current, terminal);
        return terminal;
    }

    private void event(UUID executionId, UUID taskId, AgentEventType type, String summary) {
        eventSink.record(new AgentExecutionEvent(executionId, taskId, type, summary, Instant.now()));
    }
}
