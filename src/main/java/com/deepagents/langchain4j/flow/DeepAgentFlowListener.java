package com.deepagents.langchain4j.flow;

import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.logging.ToolInvocationLogger;
import com.deepagents.langchain4j.config.DeepAgentConfig;

/**
 * Optional callbacks to observe the deep-agent run: assembled orchestrator prompt, each {@linkplain DeepAgent.Orchestrator#chat(Object, String) chat}
 * user message (truncated), every tool call (args + result, truncated), and {@code task} sub-agent delegation. For a ready-made timeline, use {@link DeepAgentFlowRecorder} or
 * {@link DeepAgentConfig.Builder#recordFlowTraceToStderr(boolean)}.
 *
 * <p>Register a custom listener via {@link DeepAgentConfig.Builder#flowListener(DeepAgentFlowListener)} or
 * {@link DeepAgent.Builder#flowListener(DeepAgentFlowListener)} (not together with {@code recordFlowTraceToStderr(true)}).
 * All methods have empty defaults — implement only what you need.
 */
public interface DeepAgentFlowListener {

    /** Fires once when the orchestrator is built, after the full system message is assembled (base + optional instructions + skills). */
    default void onOrchestratorSystemReady(String assembledSystemPrompt) {}

    /**
     * Fires at the start of each orchestrator {@code chat} (before the model runs). {@code userMessageTruncated} is produced with
     * {@link ToolInvocationLogger#truncateForLog(String)}.
     */
    default void onOrchestratorUserMessage(Object memoryId, String userMessageTruncated) {}

    /**
     * Fires after a tool finishes on the orchestrator or a sub-agent. {@code context} matches trace labels
     * ({@code orchestrator}, {@code sub-agent:name}). Strings are length-capped like {@link ToolInvocationLogger} truncation.
     */
    default void onToolInvocation(
            String context,
            String toolName,
            Object memoryId,
            String argumentsJsonTruncated,
            String resultTruncated) {}

    /** Fires when the orchestrator invokes the {@code task} tool, before the named sub-agent runs. */
    default void onSubAgentTaskStart(String subAgentType, String taskDescriptionTruncated) {}

    /** Fires when the sub-agent run returns (the string handed back to the orchestrator). */
    default void onSubAgentTaskComplete(
            String subAgentType, String taskDescriptionTruncated, String resultTruncated) {}
}
