package com.deepagents.langchain4j.demos;

import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.config.DeepAgentConfig;
import com.deepagents.langchain4j.config.OpenAiChatModelConfig;
import com.deepagents.langchain4j.flow.DeepAgentFlowRecorder;
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;
import com.deepagents.langchain4j.subagents.SubAgentDefinition;

import java.nio.file.Path;
import java.util.List;

/**
 * Standalone demo: deep agent with <strong>workspace file tools</strong>, <strong>write_todos</strong>, and
 * <strong>task</strong> sub-agents — <strong>no</strong> skills catalog. The orchestrator {@code instructions} carry the
 * full workflow; the user message is <strong>one workspace-relative path per line</strong> (default: core
 * {@code sample/BrokenStats.java} plus caller {@code sample/StatsSummary.java}). That forces cross-file reasoning,
 * ordering dependencies (fix core stats before or while fixing the caller), and a larger todo surface.
 *
 * <p><strong>Demo policy (not enforced by the library):</strong> {@code bug-finder} and {@code performance-reviewer}
 * still receive the same file tools as the orchestrator; this scenario asks them for <em>findings only</em> and assigns
 * <strong>exactly one editor</strong> — the orchestrator — so you do not double-apply patches. Other apps can instead
 * let sub-agents perform edits, or split roles differently; that is a prompt/workflow choice.
 *
 * <p>Configure the model with env: {@code OPENAI_API_KEY} (required), {@code OPENAI_MODEL} (optional, default {@code
 * gpt-4o}), {@code OPENAI_BASE_URL} (optional, for compatible APIs). Run from project root ({@code mvn exec:java}).
 */
public final class BrokenStatsJavaCodeReviewTodosFilesAndSubagentsDemo {

    /** Same layout as {@code mvn} cwd = project root. */
    private static final Path WORKSPACE = Path.of(System.getProperty("user.dir"), "workspace-demo");

    private static final String SESSION_ID = "demo-broken-stats-code-review";

    /** Default scope: core utilities + caller layer (one path per line in the user message). */
    private static final String DEMO_USER_MESSAGE =
            String.join("\n", "sample/BrokenStats.java", "sample/StatsSummary.java");

    /**
     * Prepended to the orchestrator system prompt; user turn lists workspace-relative paths (one per line).
     */
    private static final String ORCHESTRATOR_INSTRUCTIONS =
            """
            **User message convention:** the user sends **one workspace-relative path per line**. Every listed file is \
            in scope: read and fix all of them in this session (typically a small core library plus a caller that \
            misuses it). Start with read_file on each path before delegating so you know cross-references.

            Code may be demo material with intentional smells (see javadoc in each file).

            **Editing rule:** exactly **one** party may change files — **you (the orchestrator)**. Call task → \
            bug-finder and task → performance-reviewer for **written findings only**; they must **not** use write_file \
            or edit_file. In each task **description**, paste the **full path list** from the user message so \
            specialists read every relevant file. After both reports, apply every fix with your own \
            edit_file/write_file, then read_file to verify **each** changed file.

            Call both specialists (order or same-turn is fine). End with bullets of what you changed and why. Do not \
            rename public classes unless the user explicitly asks.

            **Todos (required):** Immediately after both specialist reports return, call write_todos with **at least \
            seven** concrete items spanning **both** files (e.g. BrokenStats methods, StatsSummary methods, cross-file \
            consistency, verification). Mark one in_progress before the first edit_file/write_file; after each logical \
            fix batch, call write_todos again with the **full** list updated. When fully done, every item must be \
            **completed** — do **not** finish with an empty todo list. When finished, also output JSON with "todos" and \
            "changes" arrays.
            """;

    private BrokenStatsJavaCodeReviewTodosFilesAndSubagentsDemo() {}

    private static List<SubAgentDefinition> demoTaskSpecialists() {
        return List.of(
                DeepAgent.SubAgent.builder()
                        .name("bug-finder")
                        .description(
                                """
                                Correctness findings for workspace Java: logic bugs, wrong math, null/edge cases. \
                                In this demo the orchestrator applies patches — return severity-ordered findings only.""")
                        .prompt(
                                """
                                You are a senior engineer reviewing code for defects.

                                For this demo task: return **findings only** (severity-ordered, with file/line hints). Do **not** \
                                call write_file or edit_file — the orchestrator will apply changes.

                                If the task description lists multiple Java paths, read **all** of them; avoid long \
                                list_dir tours unless paths are missing.
                                Self-contained final message for the orchestrator.
                                """)
                        .builtInFileTools(true)
                        .build(),
                DeepAgent.SubAgent.builder()
                        .name("performance-reviewer")
                        .description(
                                """
                                Performance findings: complexity, allocations, hot loops. This demo: orchestrator edits; \
                                you report only.""")
                        .prompt(
                                """
                                You are a performance reviewer.

                                For this demo: **report only** — do not call write_file or edit_file. Suggest optimizations \
                                as text for the orchestrator.

                                read_file **every** Java path named in the task description. Focus on Big-O, nested \
                                loops, redundant recomputation across call sites, string concat in loops. \
                                Self-contained final message.
                                """)
                        .builtInFileTools(true)
                        .build());
    }

    public static void main(String[] args) throws Exception {
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
                        .instructions(ORCHESTRATOR_INSTRUCTIONS)
                        .subAgents(demoTaskSpecialists())
                        .toolInvocationLogMode(ToolInvocationLogMode.NONE)
                        .recordFlowTraceToStderr(true)
                        .build();
        DeepAgent.Orchestrator agent = DeepAgent.create(harnessConfig);
        System.out.println(agent.chat(SESSION_ID, DEMO_USER_MESSAGE));
        harnessConfig.stderrFlowRecorder().ifPresent(DeepAgentFlowRecorder::printTimelineToStderr);
    }
}
