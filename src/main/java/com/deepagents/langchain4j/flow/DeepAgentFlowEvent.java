package com.deepagents.langchain4j.flow;

import java.time.Instant;

/**
 * One observable step in a deep-agent run, recorded in order by {@link DeepAgentFlowRecorder}.
 */
public sealed interface DeepAgentFlowEvent
        permits DeepAgentFlowEvent.OrchestratorSystemReady,
                DeepAgentFlowEvent.OrchestratorUserMessage,
                DeepAgentFlowEvent.ToolInvocation,
                DeepAgentFlowEvent.SubAgentTaskStart,
                DeepAgentFlowEvent.SubAgentTaskComplete {

    /** Monotonic order within this recorder (starts at 1). */
    long sequence();

    Instant timestamp();

    /** Full assembled orchestrator system message (can be large). */
    record OrchestratorSystemReady(long sequence, Instant timestamp, String assembledSystemPrompt)
            implements DeepAgentFlowEvent {}

    /** User text passed to {@link com.deepagents.langchain4j.DeepAgent.Orchestrator#chat(Object, String)} for this turn (truncated when emitted). */
    record OrchestratorUserMessage(
            long sequence, Instant timestamp, String memoryIdDisplay, String userMessageTruncated)
            implements DeepAgentFlowEvent {}

    /** After a tool finishes on the orchestrator or a sub-agent. */
    record ToolInvocation(
            long sequence,
            Instant timestamp,
            String context,
            String toolName,
            String memoryIdDisplay,
            String argumentsTruncated,
            String resultTruncated)
            implements DeepAgentFlowEvent {}

    /** Orchestrator invoked {@code task}; sub-agent run is about to start. */
    record SubAgentTaskStart(
            long sequence, Instant timestamp, String subAgentType, String taskDescriptionTruncated)
            implements DeepAgentFlowEvent {}

    /** Sub-agent returned a result string to the orchestrator. */
    record SubAgentTaskComplete(
            long sequence,
            Instant timestamp,
            String subAgentType,
            String taskDescriptionTruncated,
            String resultTruncated)
            implements DeepAgentFlowEvent {}
}
