package com.deepagents.langchain4j.task;

import com.deepagents.langchain4j.flow.DeepAgentFlowListener;
import com.deepagents.langchain4j.logging.ToolInvocationLogger;
import com.deepagents.langchain4j.subagents.SubAgentDefinition;
import com.deepagents.langchain4j.subagents.SubAgentRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the dynamic {@code task} tool: description lists sub-agents; executor dispatches by {@code subagent_type}
 * (deepagentsjs / LangChain schema); {@code subAgentType} is accepted for backward compatibility.
 */
public final class TaskToolFactory {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TaskToolFactory() {}

    /**
     * @param runtimesByName must include {@code general-purpose}
     * @param definitionsForDescription all registered sub-agents (each appears as {@code - name: description} in the task
     *     tool text, same order as registration)
     * @param flowListener optional; notified when the orchestrator calls {@code task} (before/after sub-agent run)
     */
    public static Map<ToolSpecification, ToolExecutor> create(
            Map<String, SubAgentRuntime> runtimesByName,
            List<SubAgentDefinition> definitionsForDescription,
            DeepAgentFlowListener flowListener) {

        if (!runtimesByName.containsKey("general-purpose")) {
            throw new IllegalArgumentException("runtimesByName must include 'general-purpose'");
        }

        String description = TaskToolDescriptions.buildTaskToolDescription(definitionsForDescription);

        String namesHint = runtimesByName.keySet().stream().sorted().collect(Collectors.joining(", "));

        ToolSpecification spec = ToolSpecification.builder()
                .name("task")
                .description(description)
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(
                                "description",
                                "The task to execute with the selected agent")
                        .addStringProperty(
                                "subagent_type",
                                "Name of the agent to use. Available: " + namesHint)
                        .required("description", "subagent_type")
                        .build())
                .build();

        ToolExecutor executor = (ToolExecutionRequest request, Object memoryId) -> {
            try {
                JsonNode n = JSON.readTree(request.arguments());
                String task = n.path("description").asText("");
                String type = n.path("subagent_type").asText("").trim();
                if (type.isBlank()) {
                    type = n.path("subAgentType").asText("").trim();
                }
                if (task.isBlank()) {
                    return "Error: description must not be empty";
                }
                SubAgentRuntime agent = runtimesByName.get(type);
                if (agent == null) {
                    return "Error: unknown subagent_type '" + type + "'. Available: " + namesHint;
                }
                String taskForListener = null;
                if (flowListener != null) {
                    taskForListener = ToolInvocationLogger.truncateForLog(task);
                    flowListener.onSubAgentTaskStart(type, taskForListener);
                }
                String out = agent.run(task);
                if (flowListener != null) {
                    flowListener.onSubAgentTaskComplete(
                            type, taskForListener, ToolInvocationLogger.truncateForLog(out));
                }
                return out;
            } catch (Exception e) {
                return "Error parsing task arguments or running sub-agent: " + e.getMessage();
            }
        };

        return Map.of(spec, executor);
    }
}
