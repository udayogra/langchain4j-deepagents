package com.deepagents.langchain4j.subagents;

import com.deepagents.langchain4j.flow.DeepAgentFlowListener;
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;
import com.deepagents.langchain4j.logging.ToolInvocationLogger;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.Map;

/**
 * Runs one sub-agent as an {@link AiServices} instance (tool loop inside LangChain4j).
 */
public final class SubAgentRuntime {

    private final ChatModel model;
    private final String systemPrompt;
    private final Map<ToolSpecification, ToolExecutor> tools;

    public SubAgentRuntime(
            ChatModel model,
            String systemPrompt,
            Map<ToolSpecification, ToolExecutor> tools) {
        this(model, systemPrompt, tools, "sub-agent");
    }

    /**
     * @param toolLoggingContext label for {@link ToolInvocationLogger}, e.g. {@code sub-agent:code-reviewer}
     */
    public SubAgentRuntime(
            ChatModel model,
            String systemPrompt,
            Map<ToolSpecification, ToolExecutor> tools,
            String toolLoggingContext) {
        this(model, systemPrompt, tools, toolLoggingContext, ToolInvocationLogMode.INFO);
    }

    /**
     * @param logMode {@link ToolInvocationLogMode#NONE} skips wrapping (no per-tool log lines); {@link ToolInvocationLogMode#DEBUG}
     *     logs at DEBUG; {@link ToolInvocationLogMode#INFO} at INFO. For {@code NONE} with flow callbacks, use the 6-arg constructor.
     */
    public SubAgentRuntime(
            ChatModel model,
            String systemPrompt,
            Map<ToolSpecification, ToolExecutor> tools,
            String toolLoggingContext,
            ToolInvocationLogMode logMode) {
        this(model, systemPrompt, tools, toolLoggingContext, logMode, null);
    }

    /**
     * @param flowListener optional {@link DeepAgentFlowListener} for sub-agent tool invocations (same truncation as orchestrator)
     */
    public SubAgentRuntime(
            ChatModel model,
            String systemPrompt,
            Map<ToolSpecification, ToolExecutor> tools,
            String toolLoggingContext,
            ToolInvocationLogMode logMode,
            DeepAgentFlowListener flowListener) {
        this.model = model;
        this.systemPrompt = systemPrompt;
        Map<ToolSpecification, ToolExecutor> base = tools == null ? Map.of() : Map.copyOf(tools);
        this.tools = ToolInvocationLogger.wrapAll(base, toolLoggingContext, logMode, flowListener);
    }

    /**
     * @param taskDescription full user message for the sub-agent
     * @return final assistant text (after any internal tool calls)
     */
    public String run(String taskDescription) {
        interface SubBot {
            String reply(@UserMessage String message);
        }

        var builder = AiServices.builder(SubBot.class)
                .chatModel(model)
                .systemMessageProvider(oid -> systemPrompt)
                .maxSequentialToolsInvocations(25);

        if (!tools.isEmpty()) {
            builder.tools(tools);
        }

        return builder.build().reply(taskDescription);
    }
}
