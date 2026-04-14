package com.deepagents.langchain4j.demos;

import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.config.DeepAgentConfig;
import com.deepagents.langchain4j.config.OpenAiChatModelConfig;
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;

import java.nio.file.Path;
import java.util.List;

/**
 * Standalone demo: same harness as the BrokenStats demo, but registers a <strong>fixed</strong> skills root so the system
 * prompt includes a compact {@code SKILL.md} catalog (progressive disclosure). Skills live only under
 * {@code workspace-demo/demos/skills-sample/} (three sample folders with {@code SKILL.md}).
 *
 * <p>Hardcoded: workspace, skill root list, session id, user message, model env. No extra {@code task} sub-agents (only
 * {@code general-purpose}). No CLI flags. Run from project root.
 */
public final class SkillsMarkdownCatalogProgressiveDisclosureDemo {

    private static final Path WORKSPACE = Path.of(System.getProperty("user.dir"), "workspace-demo");

    /**
     * Single search root for this demo; immediate subfolders each contain one {@code SKILL.md}. Must stay inside
     * {@link #WORKSPACE}.
     */
    private static final List<Path> SKILL_SOURCE_ROOTS =
            List.of(WORKSPACE.resolve("demos/skills-sample"));

    private static final String SESSION_ID = "demo-skills-catalog";

    /** Fixed task: exercise read_file on a skill body after seeing the catalog in the system prompt. */
    private static final String USER_MESSAGE =
            """
            The system prompt listed available skills with paths under demos/skills-sample.

            Use read_file to load the full SKILL.md for the **bug-triage** skill only. Then write one short paragraph \
            summarizing what that skill tells you to do. Do not edit any workspace files.
            """;

    private SkillsMarkdownCatalogProgressiveDisclosureDemo() {}

    public static void main(String[] args) throws Exception {
        System.err.println(
                ">>> Running " + SkillsMarkdownCatalogProgressiveDisclosureDemo.class.getName());
        OpenAiChatModelConfig modelConfig;
        try {
            modelConfig = OpenAiChatModelConfig.fromRequiredEnvironment();
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        DeepAgentConfig harnessConfig =
                DeepAgentConfig.builder()
                        .workspace(WORKSPACE)
                        .openAi(modelConfig)
                        .skillSourceRoots(SKILL_SOURCE_ROOTS)
                        .toolInvocationLogMode(ToolInvocationLogMode.DEBUG)
                        .build();
        DeepAgent.Orchestrator agent = DeepAgent.create(harnessConfig);
        System.out.println(agent.chat(SESSION_ID, USER_MESSAGE));
    }
}
