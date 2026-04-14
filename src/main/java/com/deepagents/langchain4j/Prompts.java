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

    /** Verbatim deepagentsjs {@code DEFAULT_GENERAL_PURPOSE_DESCRIPTION} ({@code subagents.ts}). */
    public static final String DEFAULT_GENERAL_PURPOSE_DESCRIPTION =
            "General-purpose agent for researching complex questions, searching for files and content, and executing "
                    + "multi-step tasks. When you are searching for a keyword or file and are not confident that you will "
                    + "find the right match in the first few tries use this agent to perform the search for you. This agent "
                    + "has access to all tools as the main agent.";

    /**
     * Verbatim deepagentsjs {@code TASK_SYSTEM_PROMPT} ({@code subagents.ts}) — appended to the orchestrator system message
     * (mirrors {@code createSubAgentMiddleware} {@code wrapModelCall}).
     */
    public static final String TASK_SYSTEM_PROMPT = ResourceTexts.load("task_system_prompt.txt");

    /**
     * Verbatim Deep Agents {@code BASE_AGENT_PROMPT} ({@code base_agent_prompt.txt}) — same as Python default system prompt.
     */
    public static final String ORCHESTRATOR_SYSTEM = ResourceTexts.load("base_agent_prompt.txt");

    /**
     * LangChain JS {@code TODO_LIST_MIDDLEWARE_SYSTEM_PROMPT} from {@code todoListMiddleware.ts} — appended to the orchestrator
     * system message when the harness registers {@code write_todos} (mirrors middleware {@code wrapModelCall} concatenation).
     */
    public static final String TODO_LIST_MIDDLEWARE_SYSTEM =
            ResourceTexts.load("todo_list_middleware_system_prompt.txt");
}
