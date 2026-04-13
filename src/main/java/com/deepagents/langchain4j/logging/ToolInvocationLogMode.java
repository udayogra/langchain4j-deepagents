package com.deepagents.langchain4j.logging;

/**
 * How {@link ToolInvocationLogger} records each tool call. Orchestrator and sub-agents share one mode from
 * {@link com.deepagents.langchain4j.config.DeepAgentConfig} / {@link com.deepagents.langchain4j.DeepAgent.Builder}.
 */
public enum ToolInvocationLogMode {

    /** No extra log lines for tool calls. */
    NONE,

    /** Log each invocation at SLF4J {@code INFO} (verbose; good for demos). */
    INFO,

    /** Log each invocation at SLF4J {@code DEBUG}; silent unless that logger is DEBUG-enabled. */
    DEBUG
}
