package com.deepagents.langchain4j.subagents;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.Map;
import java.util.Objects;

/**
 * Declares one sub-agent type the parent can invoke via the {@code task} tool.
 *
 * <p>Build instances with {@link com.deepagents.langchain4j.DeepAgent.SubAgent#builder()}. With {@code builtInFileTools(true)}
 * (the default), the sub-agent gets the same <em>built-in</em> workspace file tools as {@link com.deepagents.langchain4j.DeepAgent}
 * ({@code list_dir},
 * {@code read_file}, etc.), i.e. tools provided by this library, not your {@link #extraTools()}. With
 * {@code builtInFileTools(false)}, the specialist is text-in / text-out only (no harness file tools).
 *
 * <p>Tools are merged in order: built-in file tools (if enabled), then {@link com.deepagents.langchain4j.config.DeepAgentConfig#additionalTools()}
 * from the parent config, then {@link #extraTools()} for this definition. Avoid tool names that collide with built-ins
 * ({@code task}, {@code write_todos}, file tools) unless you intend to replace them.
 *
 * @param name        Unique id matching {@code subAgentType} in tool calls.
 * @param description Shown to the orchestrator model inside the {@code task} tool description.
 * @param systemPrompt Instructions for the sub-agent (does not inherit the orchestrator system prompt).
 * @param useBuiltInFileTools When true, the sub-agent receives the harness built-in workspace file tools (same map as the orchestrator’s file sandbox).
 * @param extraTools Your additional LangChain4j tools for this sub-agent only (merged after shared {@code additionalTools}).
 */
public record SubAgentDefinition(
        String name,
        String description,
        String systemPrompt,
        boolean useBuiltInFileTools,
        Map<ToolSpecification, ToolExecutor> extraTools) {

    public SubAgentDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(extraTools, "extraTools");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        extraTools = Map.copyOf(extraTools);
    }
}
