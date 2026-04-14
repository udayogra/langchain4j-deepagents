package com.deepagents.langchain4j.task;

import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.Prompts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskToolDescriptionsTest {

    @Test
    void buildTaskToolDescriptionIncludesSubAgents() {
        var defs =
                List.of(
                        DeepAgent.SubAgent.builder()
                                .name("general-purpose")
                                .description(Prompts.DEFAULT_GENERAL_PURPOSE_DESCRIPTION)
                                .prompt("sys")
                                .builtInFileTools(false)
                                .build(),
                        DeepAgent.SubAgent.builder()
                                .name("alpha")
                                .description("Does A")
                                .prompt("sys")
                                .builtInFileTools(false)
                                .build(),
                        DeepAgent.SubAgent.builder()
                                .name("beta")
                                .description("Does B")
                                .prompt("sys")
                                .builtInFileTools(false)
                                .build());
        String full = TaskToolDescriptions.buildTaskToolDescription(defs);
        assertTrue(full.contains("- general-purpose:"));
        assertTrue(full.contains("- alpha: Does A"));
        assertTrue(full.contains("- beta: Does B"));
        assertTrue(full.contains("Launch an ephemeral subagent"));
        assertTrue(full.contains("Lebron James"));
    }
}
