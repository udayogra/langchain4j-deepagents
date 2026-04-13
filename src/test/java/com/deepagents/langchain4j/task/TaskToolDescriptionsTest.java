package com.deepagents.langchain4j.task;

import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.subagents.SubAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskToolDescriptionsTest {

    @Test
    void buildTaskToolDescriptionIncludesSubAgents() {
        var defs =
                List.of(
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
        String bullets = TaskToolDescriptions.buildExtraSubAgentBulletList(defs);
        String full = TaskToolDescriptions.buildTaskToolDescription(bullets);
        assertTrue(full.contains("- alpha: Does A"));
        assertTrue(full.contains("- beta: Does B"));
        assertTrue(full.contains("Launch a new agent"));
    }
}
