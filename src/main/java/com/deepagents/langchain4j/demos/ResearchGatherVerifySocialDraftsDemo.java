package com.deepagents.langchain4j.demos;

import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.flow.DeepAgentFlowRecorder;
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;
import com.deepagents.langchain4j.config.DeepAgentConfig;
import com.deepagents.langchain4j.config.OpenAiChatModelConfig;
import com.deepagents.langchain4j.subagents.SubAgentDefinition;

import java.nio.file.Path;
import java.util.List;

/**
 * Standalone app-shaped demo: <strong>sub-agents</strong> gather and verify research (files in {@code research/},
 * {@code verification/}); <strong>skills</strong> (progressive disclosure) define how to draft <strong>LinkedIn</strong>,
 * <strong>Twitter/X</strong>, and <strong>Facebook</strong> posts into {@code drafts/} — only for platforms named in
 * {@code topic.txt}. The orchestrator runs the pipeline
 * with {@code task} + {@code read_file} on {@code SKILL.md} files + {@code write_file}.
 *
 * <p>Workspace: {@code workspace-demo/demos/research-verify-example/}. No live web; research uses {@code topic.txt} and
 * general knowledge. SLF4J per-tool logging is off ({@link ToolInvocationLogMode#NONE}). Flip {@link #RECORD_FLOW_TRACE_TO_STDERR}
 * to {@code true} to print a structured flow timeline to stderr after the run (independent of SLF4J).
 */
public final class ResearchGatherVerifySocialDraftsDemo {

    private static final Path WORKSPACE =
            Path.of(System.getProperty("user.dir"), "workspace-demo", "demos", "research-verify-example");

    private static final List<Path> SKILL_SOURCE_ROOTS = List.of(WORKSPACE.resolve("skills"));

    private static final String SESSION_ID = "demo-research-social-drafts";

    /** When {@code true}, {@link DeepAgentConfig.Builder#recordFlowTraceToStderr(boolean)} is enabled and the timeline prints to stderr after chat. */
    private static final boolean RECORD_FLOW_TRACE_TO_STDERR = true;

    /** Prepended to the default orchestrator system prompt: demo-specific roles, paths, and sub-agent names. */
    private static final String ORCHESTRATOR_INSTRUCTIONS =
            """
            You run the **research → verification → social drafts** demo in this workspace.

            **Sub-agents (via `task`):** `research-collector` must **write** `research/brief.md` via `write_file`. \
            `research-verifier` must **write** `verification/report.md` via `write_file` (first line `STATUS: PASS` or `STATUS: NEEDS_MORE`). \
            `research-gap-fill` must **write** the revised `research/brief.md` via `write_file`. Do not treat sub-agent **chat text** as a substitute for these files.

            **Ground truth on disk:** After every `task` `research-verifier`, you **must** `read_file verification/report.md` and branch on its **first line** only. \
            If it is `STATUS: NEEDS_MORE`, run `task` `research-gap-fill` then `task` `research-verifier` again (one gap-fill round max), then `read_file verification/report.md` again. \
            **Do not** `read_file` any post `SKILL.md` or write anything under `drafts/` until the first line of `verification/report.md` is exactly `STATUS: PASS`.

            **Skills:** progressive disclosure — only after PASS above; only platforms named in `topic.txt`. Base posts only on `research/brief.md`.

            Follow the user’s message for the exact step list and platform rules; stay inside the workspace sandbox.
            """;

    private static final String USER_MESSAGE =
            """
            **Social platforms (read this first):** After you read `topic.txt`, produce post drafts **only** for social \
            platforms **explicitly named there** (e.g. LinkedIn, Facebook, Twitter/X). The skills catalog may list more \
            skills than you need — **ignore** any platform not named in `topic.txt`: do **not** `read_file` its \
            `SKILL.md` and do **not** write its draft file. If `topic.txt` names one platform, write one draft; two names → two drafts; etc.

            You orchestrate: **research (sub-agents) → verified brief → social drafts for requested platforms only (skills)**.

            1) Read `topic.txt` and note which platforms to support.
            2) Use `write_todos` for: gather research, verify, optional gap-fill, read only the needed post skills, write only those drafts.

            **Research (task sub-agents only — do not use skills for this phase):**
            3) **task** `research-collector` — must use `write_file` so `research/brief.md` exists on disk.
            4) **task** `research-verifier` — must use `write_file` so `verification/report.md` exists (first line `STATUS: PASS` or `STATUS: NEEDS_MORE`). \
            Then **you** `read_file verification/report.md` and use **only that file’s first line** to decide the next step (not the task return text alone).
            5) If that first line is `STATUS: NEEDS_MORE`, **task** `research-gap-fill` (must `write_file` the updated `research/brief.md`) then **task** `research-verifier` again, \
            then `read_file verification/report.md` again (one gap-fill round max).

            **Posts (skills — progressive disclosure):** only after `read_file verification/report.md` shows first line exactly `STATUS: PASS`, and **only for platforms named in `topic.txt`**.
            6) For each requested platform, `read_file` the matching playbook and write the matching draft (create `drafts/` if needed). \
            Base every draft only on `research/brief.md`.
               - LinkedIn → `skills/linkedin-post/SKILL.md` → `drafts/linkedin.md`
               - Twitter / X → `skills/twitter-post/SKILL.md` → `drafts/twitter.md`
               - Facebook → `skills/facebook-post/SKILL.md` → `drafts/facebook.md`

            End with a one-paragraph summary for the user: topic, final verification status, and where each draft file lives.
            No invented URLs. Stay in the workspace sandbox.
            """;

    private ResearchGatherVerifySocialDraftsDemo() {}

    private static List<SubAgentDefinition> demoSubagents() {
        return List.of(
                DeepAgent.SubAgent.builder()
                        .name("research-collector")
                        .description(
                                "Gathers research into research/brief.md from the delegated task (general knowledge; structured brief).")
                        .prompt(
                                """
                                You are research-collector. Non-interactive; one run.

                                Your assignment is the delegated task message for this run (what the orchestrator passed via `task`). \
                                Follow that scope; only read `topic.txt` if the task text alone does not give you enough to write the brief.

                                You **must** call `write_file` to create or overwrite `research/brief.md` (do not only describe the brief in chat). \
                                Use this structure (Markdown sections ok):
                                - **Topic** — one line restating the question.
                                - **Summary** — 3–6 neutral sentences.
                                - **Key points** — 5–10 bullets; mark uncertain items with *(uncertain)*.
                                - **Risks / limitations** — at least 2 bullets.
                                - **Open questions** — 2–5 bullets a verifier might challenge.
                                - **Sources** — use exactly: `Sources: general knowledge (no URLs fetched in this demo).`

                                No HTTP. Final message: one line confirming `research/brief.md` was written (after `write_file` succeeded).
                                """)
                        .build(),
                DeepAgent.SubAgent.builder()
                        .name("research-verifier")
                        .description(
                                "Checks research/brief.md; writes verification/report.md with STATUS PASS or NEEDS_MORE and GAPS.")
                        .prompt(
                                """
                                You are research-verifier. Non-interactive; one run.

                                Read `research/brief.md`. You **must** call `write_file` to create or overwrite `verification/report.md` \
                                (create `verification/` if needed). Putting STATUS only in your assistant reply is **not** enough — the orchestrator reads the file on disk.
                                **First line of the file only:** `STATUS: PASS` or `STATUS: NEEDS_MORE`
                                Then blank line, then **Findings** (2–4 sentences on coverage, balance, clarity).
                                If NEEDS_MORE, add **GAPS** with numbered items for research-gap-fill to fix.
                                Do not edit `research/brief.md`. Final message: one line echoing the STATUS line you wrote to the file (e.g. `STATUS: PASS`).
                                """)
                        .build(),
                DeepAgent.SubAgent.builder()
                        .name("research-gap-fill")
                        .description("Updates research/brief.md using numbered GAPS from verification/report.md.")
                        .prompt(
                                """
                                You are research-gap-fill. Non-interactive; one run.

                                Read `verification/report.md` and `research/brief.md`. If the report’s first line was `STATUS: NEEDS_MORE`, \
                                revise the brief to address every numbered GAP; keep the same section headings (Topic, Summary, Key points, etc.). \
                                Mark new uncertainty with *(uncertain)*. Do not remove the Topic line.
                                You **must** call `write_file` to save the full revised content to `research/brief.md` (do not only return the brief in chat).

                                Final message: one line confirming `research/brief.md` was updated on disk.
                                """)
                        .build());
    }

    public static void main(String[] args) throws Exception {
        System.err.println(">>> Running " + ResearchGatherVerifySocialDraftsDemo.class.getName());
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
                        .subAgents(demoSubagents())
                        .instructions(ORCHESTRATOR_INSTRUCTIONS)
                        .toolInvocationLogMode(ToolInvocationLogMode.NONE)
                        .recordFlowTraceToStderr(RECORD_FLOW_TRACE_TO_STDERR)
                        .build();
        DeepAgent.Orchestrator agent = DeepAgent.create(harnessConfig);
        System.out.println(agent.chat(SESSION_ID, USER_MESSAGE));
        harnessConfig.stderrFlowRecorder().ifPresent(DeepAgentFlowRecorder::printTimelineToStderr);
    }
}
