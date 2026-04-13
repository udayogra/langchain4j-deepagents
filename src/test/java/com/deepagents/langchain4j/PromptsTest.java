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
}
