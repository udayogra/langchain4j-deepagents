package com.deepagents.langchain4j;

/**
 * Orchestrator system text shared with sub-agents (non-interactive policy, general-purpose description). Ported task-tool
 * copy lives in {@link com.deepagents.langchain4j.task.TaskToolDescriptions}.
 *
 * <p>The orchestrator system message defaults to {@code BASE_AGENT_PROMPT} from
 * <a href="https://github.com/langchain-ai/deepagents/blob/main/libs/deepagents/deepagents/graph.py">deepagents graph.py</a>
 * ({@code create_deep_agent} when {@code system_prompt is None}).
 */
public final class Prompts {

    private Prompts() {}

    /**
     * Sub-agents have no user chat; their final text is returned to the orchestrator only once.
     */
    public static final String NON_INTERACTIVE_SUBAGENT =
            """

            Non-interactive: you cannot ask follow-up questions. Complete the delegated task in this run. Do not end with \
            “Would you like me to…” or ask whether to proceed. If blocked, say what is missing in your final message.

            Workspace efficiency: if the task description already includes file contents (quoted text, markdown code fence, or \
            “excerpt from …”), use that as the primary source for the delegated work—call read_file only when you need lines \
            not included, the file may have changed since, or no snippet was provided.""";

    public static final String DEFAULT_GENERAL_PURPOSE_DESCRIPTION =
            "General-purpose agent with workspace file tools for research, drafting, and multi-step tasks.";

    /**
     * Verbatim Deep Agents {@code BASE_AGENT_PROMPT} ({@code base_agent_prompt.txt}) — same as Python default system prompt.
     */
    public static final String ORCHESTRATOR_SYSTEM = ResourceTexts.load("base_agent_prompt.txt");
}
