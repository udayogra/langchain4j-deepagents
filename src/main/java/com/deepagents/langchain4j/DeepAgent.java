package com.deepagents.langchain4j;

import com.deepagents.langchain4j.config.DeepAgentConfig;
import com.deepagents.langchain4j.flow.DeepAgentFlowListener;
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;
import com.deepagents.langchain4j.logging.ToolInvocationLogger;
import com.deepagents.langchain4j.config.OpenAiChatModelConfig;
import com.deepagents.langchain4j.files.FileToolFactory;
import com.deepagents.langchain4j.files.WorkspaceFileOperations;
import com.deepagents.langchain4j.skills.SkillMetadata;
import com.deepagents.langchain4j.subagents.SubAgentDefinition;
import com.deepagents.langchain4j.subagents.SubAgentRuntime;
import com.deepagents.langchain4j.task.TaskToolFactory;
import com.deepagents.langchain4j.todos.TodoToolFactory;
import com.deepagents.langchain4j.skills.SkillsPromptFormatter;
import com.deepagents.langchain4j.skills.SkillsScanner;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wires an orchestrator {@link AiServices} with todos, workspace files, and a {@code task} tool (sub-agents).
 * Optional {@link DeepAgentConfig#additionalTools()} (or the {@code additionalTools} argument on
 * {@link #create(ChatModel, Path, List, List, int, int, Map)}) are merged with those built-ins on the main agent and
 * shared with every sub-agent; use {@link SubAgentDefinition#extraTools()} for per-sub-agent tools only.
 * Pure LangChain4j — no Spring AI.
 *
 * <p>Todos are scoped by {@code @MemoryId} (same string you pass to {@link Orchestrator#chat(Object, String)}): each
 * session id gets its own {@link com.deepagents.langchain4j.todos.TodoStore}. The workspace directory is still shared;
 * for strict per-user disk isolation, use a different workspace root (or subdirectory policy) per tenant in your app
 * layer.
 */
public final class DeepAgent {

    public interface Orchestrator {
        String chat(@MemoryId String sessionId, @UserMessage String userMessage);
    }

    private static final String GENERAL_PURPOSE_BODY =
            """
            You are a general-purpose sub-agent. When the task involves files in the workspace, read or change them as needed.
            Produce a clear final answer in plain text; the orchestrator only sees this result.
            """;

    private static String subAgentSystemWithSkillsSection(String basePrompt, String skillsSection) {
        String s = basePrompt + Prompts.NON_INTERACTIVE_SUBAGENT;
        if (skillsSection != null && !skillsSection.isBlank()) {
            s = s + "\n\n" + skillsSection;
        }
        return s;
    }

    /**
     * Builds the orchestrator from {@link DeepAgentConfig}: either {@link DeepAgentConfig#openAi()} (minimal
     * API key + model name) or {@link DeepAgentConfig#chatModel()} (pre-built {@link ChatModel}), plus workspace,
     * skills, sub-agents, and memory/tool limits.
     */
    public static Orchestrator create(DeepAgentConfig config) throws java.io.IOException {
        ChatModel model;
        if (config.chatModel() != null) {
            model = config.chatModel();
        } else {
            model = Objects.requireNonNull(config.openAi()).toChatModel();
        }
        return createImpl(
                model,
                config.workspace(),
                config.skillSourceRoots(),
                config.subAgents(),
                config.chatMemoryMaxMessages(),
                config.maxSequentialToolInvocations(),
                config.additionalTools(),
                config.orchestratorInstructions(),
                config.toolInvocationLogMode(),
                config.flowListener());
    }

    /**
     * @param workspace sandbox root for file tools (created if missing)
     * @param extraSubAgents additional specialists (names must not be {@code general-purpose})
     */
    public static Orchestrator create(ChatModel model, Path workspace, List<SubAgentDefinition> extraSubAgents)
            throws java.io.IOException {
        return create(model, workspace, List.of(), extraSubAgents);
    }

    /**
     * Same as {@link #create(ChatModel, Path, List)} but discovers optional skills under {@code skillSourceRoots}
     * (directories whose immediate subfolders contain {@code SKILL.md}). Catalog text is appended to the orchestrator and
     * sub-agent system prompts (progressive disclosure); the model loads full instructions with {@code read_file}.
     *
     * @param skillSourceRoots roots to scan, each must resolve inside {@code workspace}; empty skips skills
     */
    public static Orchestrator create(
            ChatModel model,
            Path workspace,
            List<Path> skillSourceRoots,
            List<SubAgentDefinition> extraSubAgents)
            throws java.io.IOException {
        return create(
                model,
                workspace,
                skillSourceRoots,
                extraSubAgents,
                DeepAgentConfig.DEFAULT_CHAT_MEMORY_MAX_MESSAGES,
                DeepAgentConfig.DEFAULT_MAX_SEQUENTIAL_TOOL_INVOCATIONS,
                Map.of());
    }

    /**
     * Same as {@link #create(ChatModel, Path, List, List)} with explicit orchestrator memory window and tool-step limit.
     */
    public static Orchestrator create(
            ChatModel model,
            Path workspace,
            List<Path> skillSourceRoots,
            List<SubAgentDefinition> extraSubAgents,
            int chatMemoryMaxMessages,
            int maxSequentialToolInvocations)
            throws java.io.IOException {
        return create(
                model,
                workspace,
                skillSourceRoots,
                extraSubAgents,
                chatMemoryMaxMessages,
                maxSequentialToolInvocations,
                Map.of());
    }

    /**
     * Full constructor-path: same as {@link #create(ChatModel, Path, List, List, int, int)} plus {@code additionalTools}
     * merged into the orchestrator and every sub-agent (same role as {@link DeepAgentConfig#additionalTools()}).
     */
    public static Orchestrator create(
            ChatModel model,
            Path workspace,
            List<Path> skillSourceRoots,
            List<SubAgentDefinition> extraSubAgents,
            int chatMemoryMaxMessages,
            int maxSequentialToolInvocations,
            Map<ToolSpecification, ToolExecutor> additionalTools)
            throws java.io.IOException {
        return createImpl(
                model,
                workspace,
                skillSourceRoots,
                extraSubAgents,
                chatMemoryMaxMessages,
                maxSequentialToolInvocations,
                additionalTools,
                null,
                DeepAgentConfig.DEFAULT_TOOL_INVOCATION_LOG_MODE,
                null);
    }

    /**
     * Fluent builder (similar ergonomics to other Deep Agents Java APIs): {@link SubAgent#builder()} for specialists,
     * then {@link #builder()} for workspace, model, optional {@link Builder#instructions(String)}, and shared tools.
     */
    public static Builder builder() {
        return new Builder();
    }

    private static Orchestrator createImpl(
            ChatModel model,
            Path workspace,
            List<Path> skillSourceRoots,
            List<SubAgentDefinition> extraSubAgents,
            int chatMemoryMaxMessages,
            int maxSequentialToolInvocations,
            Map<ToolSpecification, ToolExecutor> additionalTools,
            String orchestratorInstructionsPrefix,
            ToolInvocationLogMode toolInvocationLogMode,
            DeepAgentFlowListener flowListener)
            throws java.io.IOException {
        Objects.requireNonNull(additionalTools, "additionalTools");
        Objects.requireNonNull(toolInvocationLogMode, "toolInvocationLogMode");
        Files.createDirectories(workspace);

        Map<ToolSpecification, ToolExecutor> sharedAdditional = Map.copyOf(additionalTools);

        List<Path> roots = skillSourceRoots == null ? List.of() : skillSourceRoots;
        List<SkillMetadata> skills = SkillsScanner.scan(workspace, roots);
        String skillsSection = SkillsPromptFormatter.formatSection(workspace, roots, skills);
        String baseWithSkills =
                skillsSection.isBlank()
                        ? Prompts.ORCHESTRATOR_SYSTEM
                        : Prompts.ORCHESTRATOR_SYSTEM + "\n\n" + skillsSection;
        String orchestratorSystem =
                orchestratorInstructionsPrefix == null || orchestratorInstructionsPrefix.isBlank()
                        ? baseWithSkills
                        : orchestratorInstructionsPrefix.strip() + "\n\n" + baseWithSkills;
        if (flowListener != null) {
            flowListener.onOrchestratorSystemReady(orchestratorSystem);
        }

        WorkspaceFileOperations fileOps = new WorkspaceFileOperations(workspace);

        Map<ToolSpecification, ToolExecutor> todoTools = TodoToolFactory.buildPerMemoryId();
        Map<ToolSpecification, ToolExecutor> fileTools = FileToolFactory.build(fileOps);

        Map<String, SubAgentRuntime> runtimes = new LinkedHashMap<>();
        runtimes.put(
                "general-purpose",
                new SubAgentRuntime(
                        model,
                        subAgentSystemWithSkillsSection(GENERAL_PURPOSE_BODY, skillsSection),
                        mergeSubAgentTools(true, fileTools, sharedAdditional, Map.of()),
                        "sub-agent:general-purpose",
                        toolInvocationLogMode,
                        flowListener));

        List<SubAgentDefinition> descriptions = new ArrayList<>();
        descriptions.add(
                SubAgent.builder()
                        .name("general-purpose")
                        .description(Prompts.DEFAULT_GENERAL_PURPOSE_DESCRIPTION)
                        .prompt(subAgentSystemWithSkillsSection(GENERAL_PURPOSE_BODY, skillsSection))
                        .build());

        for (SubAgentDefinition d : extraSubAgents) {
            if ("general-purpose".equalsIgnoreCase(d.name())) {
                throw new IllegalArgumentException("Sub-agent name 'general-purpose' is reserved");
            }
            if (runtimes.containsKey(d.name())) {
                throw new IllegalArgumentException("Duplicate sub-agent name: " + d.name());
            }
            Map<ToolSpecification, ToolExecutor> subTools =
                    mergeSubAgentTools(d.useBuiltInFileTools(), fileTools, sharedAdditional, d.extraTools());
            runtimes.put(
                    d.name(),
                    new SubAgentRuntime(
                            model,
                            subAgentSystemWithSkillsSection(d.systemPrompt(), skillsSection),
                            subTools,
                            "sub-agent:" + d.name(),
                            toolInvocationLogMode,
                            flowListener));
            descriptions.add(d);
        }

        Map<ToolSpecification, ToolExecutor> taskTools = TaskToolFactory.create(runtimes, descriptions, flowListener);

        Map<ToolSpecification, ToolExecutor> orchestratorTools = new LinkedHashMap<>();
        orchestratorTools.putAll(todoTools);
        orchestratorTools.putAll(fileTools);
        orchestratorTools.putAll(taskTools);
        orchestratorTools.putAll(sharedAdditional);
        orchestratorTools =
                ToolInvocationLogger.wrapAll(orchestratorTools, "orchestrator", toolInvocationLogMode, flowListener);

        ChatMemoryProvider memoryProvider =
                memoryId -> MessageWindowChatMemory.builder().id(memoryId).maxMessages(chatMemoryMaxMessages).build();

        Orchestrator orchestrator =
                AiServices.builder(Orchestrator.class)
                        .chatModel(model)
                        .systemMessageProvider(oid -> orchestratorSystem)
                        .tools(orchestratorTools)
                        .chatMemoryProvider(memoryProvider)
                        .maxSequentialToolsInvocations(maxSequentialToolInvocations)
                        .build();
        if (flowListener != null) {
            orchestrator = new FlowTracingOrchestrator(orchestrator, flowListener);
        }
        return orchestrator;
    }

    /**
     * Delegates to the real {@link AiServices} orchestrator and notifies {@link DeepAgentFlowListener#onOrchestratorUserMessage}
     * before each {@link Orchestrator#chat(Object, String)} (truncated user text).
     */
    private static final class FlowTracingOrchestrator implements Orchestrator {
        private final Orchestrator delegate;
        private final DeepAgentFlowListener listener;

        FlowTracingOrchestrator(Orchestrator delegate, DeepAgentFlowListener listener) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.listener = Objects.requireNonNull(listener, "listener");
        }

        @Override
        public String chat(String sessionId, String userMessage) {
            String truncated =
                    userMessage == null ? "" : ToolInvocationLogger.truncateForLog(userMessage);
            listener.onOrchestratorUserMessage(sessionId, truncated);
            return delegate.chat(sessionId, userMessage);
        }
    }

    /**
     * Harness built-in file tools (if enabled), then shared {@code additionalTools}, then per-definition {@code extraTools}.
     */
    private static Map<ToolSpecification, ToolExecutor> mergeSubAgentTools(
            boolean useBuiltInFileTools,
            Map<ToolSpecification, ToolExecutor> fileTools,
            Map<ToolSpecification, ToolExecutor> additionalTools,
            Map<ToolSpecification, ToolExecutor> definitionExtra) {
        Map<ToolSpecification, ToolExecutor> m = new LinkedHashMap<>();
        if (useBuiltInFileTools) {
            m.putAll(fileTools);
        }
        m.putAll(additionalTools);
        m.putAll(definitionExtra);
        return m;
    }

    /**
     * Declarative sub-agent spec; {@link #builder()} produces a {@link SubAgentDefinition} for {@link Builder#subAgents(List)}.
     */
    public static final class SubAgent {

        private SubAgent() {}

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String name;
            private String description;
            private String prompt;
            private boolean builtInFileTools = true;
            private final LinkedHashMap<ToolSpecification, ToolExecutor> extraTools = new LinkedHashMap<>();

            public Builder name(String value) {
                this.name = value;
                return this;
            }

            public Builder description(String value) {
                this.description = value;
                return this;
            }

            /** System prompt for this specialist (shown only to the sub-agent, not the orchestrator). */
            public Builder prompt(String value) {
                this.prompt = value;
                return this;
            }

            /**
             * Extra LangChain4j tools for this sub-agent only (merged after shared orchestrator {@code additionalTools}).
             * For string-named tools (e.g. Spring style), register {@link ToolSpecification} + {@link ToolExecutor} yourself.
             */
            public Builder tools(Map<ToolSpecification, ToolExecutor> map) {
                this.extraTools.clear();
                if (map != null) {
                    this.extraTools.putAll(map);
                }
                return this;
            }

            public Builder addTool(ToolSpecification spec, ToolExecutor executor) {
                this.extraTools.put(Objects.requireNonNull(spec), Objects.requireNonNull(executor));
                return this;
            }

            /** When {@code false}, no harness file tools (text-in / text-out only). Default {@code true}. */
            public Builder builtInFileTools(boolean enabled) {
                this.builtInFileTools = enabled;
                return this;
            }

            public SubAgentDefinition build() {
                Objects.requireNonNull(name, "name");
                Objects.requireNonNull(description, "description");
                Objects.requireNonNull(prompt, "prompt");
                if (name.isBlank()) {
                    throw new IllegalArgumentException("name must not be blank");
                }
                return new SubAgentDefinition(name, description, prompt, builtInFileTools, Map.copyOf(extraTools));
            }
        }
    }

    /** Builds an {@link Orchestrator} in one chain; throws {@link java.io.IOException} for workspace/skills scan. */
    public static final class Builder {
        private Path workspace;
        private String instructions;
        private final List<Path> skillSourceRoots = new ArrayList<>();
        private final List<SubAgentDefinition> subAgents = new ArrayList<>();
        private OpenAiChatModelConfig openAi;
        private ChatModel chatModel;
        private int chatMemoryMaxMessages = DeepAgentConfig.DEFAULT_CHAT_MEMORY_MAX_MESSAGES;
        private int maxSequentialToolInvocations = DeepAgentConfig.DEFAULT_MAX_SEQUENTIAL_TOOL_INVOCATIONS;
        private final LinkedHashMap<ToolSpecification, ToolExecutor> additionalTools = new LinkedHashMap<>();
        private ToolInvocationLogMode toolInvocationLogMode = DeepAgentConfig.DEFAULT_TOOL_INVOCATION_LOG_MODE;
        private DeepAgentFlowListener flowListener;

        private Builder() {}

        public Builder workspace(Path path) {
            this.workspace = Objects.requireNonNull(path, "workspace");
            return this;
        }

        /**
         * Prepended before the default Deep Agents orchestrator system text (and before the skills catalog), like Python
         * {@code create_deep_agent(system_prompt=..., ...)}.
         */
        public Builder instructions(String text) {
            this.instructions = text;
            return this;
        }

        public Builder addSkillSourceRoot(Path root) {
            this.skillSourceRoots.add(Objects.requireNonNull(root));
            return this;
        }

        public Builder skillSourceRoots(List<Path> roots) {
            this.skillSourceRoots.clear();
            if (roots != null) {
                this.skillSourceRoots.addAll(roots);
            }
            return this;
        }

        public Builder addSubAgent(SubAgentDefinition def) {
            this.subAgents.add(Objects.requireNonNull(def));
            return this;
        }

        public Builder subAgents(List<SubAgentDefinition> defs) {
            this.subAgents.clear();
            if (defs != null) {
                this.subAgents.addAll(defs);
            }
            return this;
        }

        public Builder openAi(OpenAiChatModelConfig config) {
            if (this.chatModel != null) {
                throw new IllegalStateException("Already set chatModel(...); use only one of openAi or chatModel.");
            }
            this.openAi = Objects.requireNonNull(config);
            return this;
        }

        public Builder chatModel(ChatModel model) {
            if (this.openAi != null) {
                throw new IllegalStateException("Already set openAi(...); use only one of openAi or chatModel.");
            }
            this.chatModel = Objects.requireNonNull(model);
            return this;
        }

        public Builder chatMemoryMaxMessages(int max) {
            if (max < 1) {
                throw new IllegalArgumentException("chatMemoryMaxMessages must be >= 1");
            }
            this.chatMemoryMaxMessages = max;
            return this;
        }

        public Builder maxSequentialToolInvocations(int max) {
            if (max < 1) {
                throw new IllegalArgumentException("maxSequentialToolInvocations must be >= 1");
            }
            this.maxSequentialToolInvocations = max;
            return this;
        }

        /** Shared tools for the orchestrator and every sub-agent (same as {@link DeepAgentConfig.Builder#additionalTools(Map)}). */
        public Builder tools(Map<ToolSpecification, ToolExecutor> map) {
            this.additionalTools.clear();
            if (map != null) {
                this.additionalTools.putAll(map);
            }
            return this;
        }

        public Builder addTool(ToolSpecification spec, ToolExecutor executor) {
            this.additionalTools.put(Objects.requireNonNull(spec), Objects.requireNonNull(executor));
            return this;
        }

        public Builder toolInvocationLogMode(ToolInvocationLogMode mode) {
            this.toolInvocationLogMode = Objects.requireNonNull(mode, "toolInvocationLogMode");
            return this;
        }

        public Builder flowListener(DeepAgentFlowListener listener) {
            this.flowListener = listener;
            return this;
        }

        public Orchestrator build() throws java.io.IOException {
            Objects.requireNonNull(workspace, "workspace");
            ChatModel model;
            if (chatModel != null) {
                model = chatModel;
            } else if (openAi != null) {
                model = openAi.toChatModel();
            } else {
                throw new IllegalStateException("Set exactly one of chatModel(...) or openAi(...).");
            }
            return createImpl(
                    model,
                    workspace,
                    skillSourceRoots,
                    subAgents,
                    chatMemoryMaxMessages,
                    maxSequentialToolInvocations,
                    Map.copyOf(additionalTools),
                    instructions,
                    toolInvocationLogMode,
                    flowListener);
        }
    }
}
