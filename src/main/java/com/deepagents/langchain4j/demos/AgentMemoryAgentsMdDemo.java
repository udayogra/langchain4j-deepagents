package com.deepagents.langchain4j.demos;

import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.config.DeepAgentConfig;
import com.deepagents.langchain4j.config.OpenAiChatModelConfig;
import com.deepagents.langchain4j.flow.DeepAgentFlowRecorder;
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;

import java.nio.file.Path;

/**
 * Standalone demo: loads {@code workspace-demo/demos/agent-memory-sample/AGENTS.md} as <strong>agent memory</strong> (injected
 * into the system prompt at {@link DeepAgent#create}, same idea as deepagentsjs {@code memory.ts}).
 *
 * <p>The user message asks the model to use only what was loaded into memory—no {@code read_file} required for the codeword.
 * Run from project root with {@code OPENAI_API_KEY} set.
 */
public final class AgentMemoryAgentsMdDemo {

    private static final Path WORKSPACE = Path.of(System.getProperty("user.dir"), "workspace-demo");

    /** Must stay under {@link #WORKSPACE} so {@code edit_file} can update it if you extend the demo. */
    private static final Path MEMORY_FILE = Path.of("demos/agent-memory-sample/AGENTS.md");

    private static final String SESSION_ID = "demo-agent-memory";

    private static final String USER_MESSAGE =
            """
            You should already have **agent memory** in your system prompt from AGENTS.md (path demos/agent-memory-sample/AGENTS.md).

            Reply in plain text only:
            1) Quote the **demo codeword** from that memory exactly.
            2) In one sentence, say what the memory says about how long your reply should be in this demo.

            Do not use read_file or any other tools for this turn.
            """;

    private AgentMemoryAgentsMdDemo() {}

    public static void main(String[] args) throws Exception {
        System.err.println(">>> Running " + AgentMemoryAgentsMdDemo.class.getName());
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
                        .addMemorySource(MEMORY_FILE)
                        .toolInvocationLogMode(ToolInvocationLogMode.DEBUG)
                        .recordFlowTraceToStderr(true)
                        .build();
        DeepAgent.Orchestrator agent = DeepAgent.create(harnessConfig);
        System.out.println(agent.chat(SESSION_ID, USER_MESSAGE));
        harnessConfig.stderrFlowRecorder().ifPresent(DeepAgentFlowRecorder::printTimelineToStderr);
    }
}
