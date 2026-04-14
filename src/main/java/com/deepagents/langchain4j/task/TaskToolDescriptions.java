package com.deepagents.langchain4j.task;

import com.deepagents.langchain4j.ResourceTexts;
import com.deepagents.langchain4j.subagents.SubAgentDefinition;

/**
 * Text assembly for the orchestrator’s {@code task} tool — verbatim deepagentsjs {@code getTaskToolDescription} body in
 * {@code task_tool_description.txt}, with {@code {agent_list}} replaced by {@code - name: description} lines (same order
 * as {@link com.deepagents.langchain4j.DeepAgent} registration).
 */
public final class TaskToolDescriptions {

    private TaskToolDescriptions() {}

    /**
     * Full {@code task} tool description: template plus dynamic agent list (includes {@code general-purpose} and all
     * configured specialists).
     */
    public static String buildTaskToolDescription(Iterable<SubAgentDefinition> subAgents) {
        String agentList = buildSubAgentListing(subAgents);
        return ResourceTexts.load("task_tool_description.txt").replace("{agent_list}", agentList);
    }

    /** One line per agent: {@code - name: description} (deepagentsjs list format). */
    public static String buildSubAgentListing(Iterable<SubAgentDefinition> subAgents) {
        StringBuilder sb = new StringBuilder();
        for (SubAgentDefinition s : subAgents) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("- ").append(s.name()).append(": ").append(s.description());
        }
        return sb.toString();
    }

    /** Bullet lines for specialists only (excludes {@code general-purpose}); for callers that build their own template. */
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
