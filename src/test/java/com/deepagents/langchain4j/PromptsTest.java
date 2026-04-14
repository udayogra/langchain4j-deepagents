package com.deepagents.langchain4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptsTest {

    @Test
    void orchestratorSystemIsDeepAgentsBaseAgentPromptOnly() {
        assertTrue(Prompts.ORCHESTRATOR_SYSTEM.contains("You are a Deep Agent"));
        assertTrue(Prompts.ORCHESTRATOR_SYSTEM.contains("## Core Behavior"));
        assertTrue(Prompts.ORCHESTRATOR_SYSTEM.contains("## Doing Tasks"));
        assertTrue(Prompts.ORCHESTRATOR_SYSTEM.contains("## Progress Updates"));
    }

    @Test
    void todoListMiddlewareSystemPrompt_matchesLangChainJsTodoListMiddleware() {
        assertTrue(Prompts.TODO_LIST_MIDDLEWARE_SYSTEM.contains("write_todos"));
        assertTrue(Prompts.TODO_LIST_MIDDLEWARE_SYSTEM.contains("never be called multiple times in parallel"));
        assertTrue(Prompts.TODO_LIST_MIDDLEWARE_SYSTEM.contains("revise the To-Do list"));
    }

    @Test
    void taskSystemPrompt_deepagentsJsSubagentsTs() {
        assertTrue(Prompts.TASK_SYSTEM_PROMPT.contains("subagent spawner"));
        assertTrue(Prompts.TASK_SYSTEM_PROMPT.contains("Reconcile"));
    }
}
