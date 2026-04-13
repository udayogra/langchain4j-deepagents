package com.deepagents.langchain4j.config;

import com.deepagents.langchain4j.flow.DeepAgentFlowListener;
import com.deepagents.langchain4j.flow.DeepAgentFlowRecorder;
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;
import com.deepagents.langchain4j.subagents.SubAgentDefinition;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecutor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * End-user configuration for {@link com.deepagents.langchain4j.DeepAgent}: workspace, optional skills roots,
 * {@code task} sub-agents, optional {@link #additionalTools()} (merged into the orchestrator and every sub-agent, like
 * Python {@code create_deep_agent(..., tools=...)}), and either a minimal OpenAI config or a pre-built {@link ChatModel}.
 *
 * <p>Set exactly one of {@link Builder#openAi(OpenAiChatModelConfig)} or {@link Builder#chatModel(ChatModel)}.
 *
 * <pre>{@code
 * // From API key + model name
 * DeepAgent.create(DeepAgentConfig.builder()
 *     .workspace(workspace)
 *     .openAi(OpenAiChatModelConfig.of(key, "gpt-4o"))
 *     .subAgents(subs)
 *     .build());
 *
 * // From your own ChatModel (any provider / options)
 * DeepAgent.create(DeepAgentConfig.builder()
 *     .workspace(workspace)
 *     .chatModel(myChatModel)
 *     .build());
 * }</pre>
 */
public final class DeepAgentConfig {

    public static final int DEFAULT_CHAT_MEMORY_MAX_MESSAGES = 48;
    public static final int DEFAULT_MAX_SEQUENTIAL_TOOL_INVOCATIONS = 35;
    public static final ToolInvocationLogMode DEFAULT_TOOL_INVOCATION_LOG_MODE = ToolInvocationLogMode.INFO;

    private final Path workspace;
    private final List<Path> skillSourceRoots;
    private final List<SubAgentDefinition> subAgents;
    private final OpenAiChatModelConfig openAi;
    private final ChatModel chatModel;
    private final int chatMemoryMaxMessages;
    private final int maxSequentialToolInvocations;
    private final Map<ToolSpecification, ToolExecutor> additionalTools;
    /** Optional; prepended before the default Deep Agents base orchestrator prompt (Python-style). */
    private final String orchestratorInstructions;
    private final ToolInvocationLogMode toolInvocationLogMode;
    private final DeepAgentFlowListener flowListener;
    /** Non-null only when {@link Builder#recordFlowTraceToStderr(boolean)} was used; same instance as {@link #flowListener()}. */
    private final DeepAgentFlowRecorder stderrFlowRecorder;

    private DeepAgentConfig(Builder b, DeepAgentFlowRecorder stderrFlowRecorder) {
        this.workspace = Objects.requireNonNull(b.workspace, "workspace");
        this.skillSourceRoots = List.copyOf(b.skillSourceRoots);
        this.subAgents = List.copyOf(b.subAgents);
        this.openAi = b.openAi;
        this.chatModel = b.chatModel;
        this.chatMemoryMaxMessages = b.chatMemoryMaxMessages;
        this.maxSequentialToolInvocations = b.maxSequentialToolInvocations;
        this.additionalTools = Map.copyOf(b.additionalTools);
        this.orchestratorInstructions = b.orchestratorInstructions;
        this.toolInvocationLogMode = Objects.requireNonNull(b.toolInvocationLogMode, "toolInvocationLogMode");
        this.flowListener = b.flowListener;
        this.stderrFlowRecorder = stderrFlowRecorder;
    }

    public Path workspace() {
        return workspace;
    }

    public List<Path> skillSourceRoots() {
        return skillSourceRoots;
    }

    public List<SubAgentDefinition> subAgents() {
        return subAgents;
    }

    /**
     * Present when the agent should build the model from {@link OpenAiChatModelConfig#toChatModel()}; mutually exclusive with
     * {@link #chatModel()}.
     */
    public OpenAiChatModelConfig openAi() {
        return openAi;
    }

    /**
     * Present when the caller supplied a pre-built model; mutually exclusive with {@link #openAi()}.
     */
    public ChatModel chatModel() {
        return chatModel;
    }

    public int chatMemoryMaxMessages() {
        return chatMemoryMaxMessages;
    }

    public int maxSequentialToolInvocations() {
        return maxSequentialToolInvocations;
    }

    /**
     * Extra tools merged into the orchestrator and into every sub-agent (after workspace file tools where enabled), before
     * each {@link SubAgentDefinition#extraTools()}.
     */
    public Map<ToolSpecification, ToolExecutor> additionalTools() {
        return additionalTools;
    }

    /**
     * When non-null and not blank, prepended to the default orchestrator system message (then skills catalog if any).
     */
    public String orchestratorInstructions() {
        return orchestratorInstructions;
    }

    /** Per-tool call logging for orchestrator and all sub-agents. */
    public ToolInvocationLogMode toolInvocationLogMode() {
        return toolInvocationLogMode;
    }

    /** Optional flow tracing; may be null. */
    public DeepAgentFlowListener flowListener() {
        return flowListener;
    }

    /**
     * Present when {@link Builder#recordFlowTraceToStderr(boolean)} was {@code true} at build time — the recorder wired as
     * {@link #flowListener()}. After {@link com.deepagents.langchain4j.DeepAgent.Orchestrator#chat(Object, String)}, call
     * {@link DeepAgentFlowRecorder#printTimelineToStderr()} or {@link DeepAgentFlowRecorder#formatTimeline()}.
     */
    public Optional<DeepAgentFlowRecorder> stderrFlowRecorder() {
        return Optional.ofNullable(stderrFlowRecorder);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path workspace;
        private final List<Path> skillSourceRoots = new ArrayList<>();
        private final List<SubAgentDefinition> subAgents = new ArrayList<>();
        private OpenAiChatModelConfig openAi;
        private ChatModel chatModel;
        private int chatMemoryMaxMessages = DEFAULT_CHAT_MEMORY_MAX_MESSAGES;
        private int maxSequentialToolInvocations = DEFAULT_MAX_SEQUENTIAL_TOOL_INVOCATIONS;
        private final Map<ToolSpecification, ToolExecutor> additionalTools = new LinkedHashMap<>();
        private String orchestratorInstructions;
        private ToolInvocationLogMode toolInvocationLogMode = DEFAULT_TOOL_INVOCATION_LOG_MODE;
        private DeepAgentFlowListener flowListener;
        private boolean recordFlowTraceToStderr;

        private Builder() {}

        public Builder workspace(Path path) {
            this.workspace = path;
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

        /** Use {@link OpenAiChatModelConfig#toChatModel()}; mutually exclusive with {@link #chatModel}. */
        public Builder openAi(OpenAiChatModelConfig config) {
            if (this.chatModel != null) {
                throw new IllegalStateException("Already set chatModel(...); use only one of openAi or chatModel.");
            }
            this.openAi = Objects.requireNonNull(config);
            return this;
        }

        /** Use a pre-built model (any LangChain4j provider); mutually exclusive with {@link #openAi}. */
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

        /**
         * Tools merged with built-ins on the main agent and merged into each sub-agent (general-purpose and extras). Later
         * entries in this map win on duplicate {@link ToolSpecification} keys when combined with other maps.
         */
        public Builder additionalTools(Map<ToolSpecification, ToolExecutor> tools) {
            this.additionalTools.clear();
            if (tools != null) {
                this.additionalTools.putAll(tools);
            }
            return this;
        }

        /**
         * Prepended before the library default orchestrator prompt (same as {@link com.deepagents.langchain4j.DeepAgent.Builder#instructions(String)}).
         */
        public Builder instructions(String text) {
            this.orchestratorInstructions = text;
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

        /**
         * When {@code true}, installs a {@link DeepAgentFlowRecorder} as {@link #flowListener(DeepAgentFlowListener)} and keeps
         * a reference on the built {@link DeepAgentConfig#stderrFlowRecorder()} so you can print the timeline after a run.
         * Mutually exclusive with {@link #flowListener(DeepAgentFlowListener)}.
         */
        public Builder recordFlowTraceToStderr(boolean enable) {
            this.recordFlowTraceToStderr = enable;
            return this;
        }

        public DeepAgentConfig build() {
            if (openAi == null && chatModel == null) {
                throw new IllegalStateException("Set exactly one of openAi(...) or chatModel(...).");
            }
            DeepAgentFlowRecorder stderrRecorder = null;
            if (recordFlowTraceToStderr) {
                if (flowListener != null) {
                    throw new IllegalStateException(
                            "Set only one of flowListener(...) or recordFlowTraceToStderr(true).");
                }
                stderrRecorder = new DeepAgentFlowRecorder();
                flowListener = stderrRecorder;
            }
            return new DeepAgentConfig(this, stderrRecorder);
        }
    }
}
