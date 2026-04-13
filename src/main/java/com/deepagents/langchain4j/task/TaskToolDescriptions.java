package com.deepagents.langchain4j.task;

import com.deepagents.langchain4j.ResourceTexts;
import com.deepagents.langchain4j.subagents.SubAgentDefinition;

/**
 * Text assembly for the orchestrator’s {@code task} tool (ported prefix/suffix resources plus sub-agent bullet list).
 */
public final class TaskToolDescriptions {

    private TaskToolDescriptions() {}

    /**
     * {@code task} tool: ported prefix/suffix; {@code {other_agents}} in prefix is replaced with extra specialist bullets only
     * (general-purpose is already in the prefix text).
     */
    public static String buildTaskToolDescription(String extraSubAgentBullets) {
        String prefix =
                ResourceTexts.load("task_description_prefix.txt")
                        .replace(
                                "{other_agents}",
                                extraSubAgentBullets.isBlank()
                                        ? ""
                                        : extraSubAgentBullets.endsWith("\n")
                                                ? extraSubAgentBullets
                                                : extraSubAgentBullets + "\n");
        String suffix = ResourceTexts.load("task_description_suffix.txt");
        String harnessNote =
                """

                ---
                This harness: invoke task with JSON { "description": "<...>", "subAgentType": "<name>" } (camelCase). \
                Choose subAgentType from the agent list above when delegation fits; otherwise use workspace operations directly.""";
        return prefix + "\n" + suffix + harnessNote;
    }

    /** Bullet lines {@code - name: description} for <em>extra</em> sub-agents (not general-purpose). */
    public static String buildExtraSubAgentBulletList(Iterable<SubAgentDefinition> subAgents) {
        StringBuilder sb = new StringBuilder();
        for (SubAgentDefinition s : subAgents) {
            if ("general-purpose".equalsIgnoreCase(s.name())) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("- ").append(s.name()).append(": ").append(s.description());
        }
        return sb.toString();
    }

    /** @deprecated use {@link #buildExtraSubAgentBulletList} */
    @Deprecated
    public static String buildOtherAgentsBulletList(Iterable<SubAgentDefinition> subAgents) {
        return buildExtraSubAgentBulletList(subAgents);
    }
}
