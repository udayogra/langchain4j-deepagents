# langchain4j-deepagents

Java **17+** library that mirrors a **[deepagents](https://github.com/langchain-ai/deepagents)-style** agent on **[LangChain4j](https://github.com/langchain4j/langchain4j)** only: orchestrator chat memory, **`write_todos`**, sandboxed **workspace file tools**, and a **`task`** tool that runs **sub-agents**. Optional **skills** (folders with `SKILL.md`) are discovered and listed in the system prompt for progressive disclosure.

Long tool and task descriptions are loaded from classpath resources under `agent-prompts/`, aligned with ports used in [langgraph4j-deepagents](https://github.com/langgraph4j/langgraph4j-deepagents) and upstream Python [deepagents `graph.py`](https://github.com/langchain-ai/deepagents/blob/main/libs/deepagents/deepagents/graph.py).

---

## Requirements

- **Java 17**
- **LangChain4j** (declared in this project’s `pom.xml`, currently `1.9.1`)
- A **chat model with tool calling** (examples below use OpenAI)

---

## Add as a dependency

After you publish the JAR (or `mvn install` locally):

```xml
<dependency>
  <groupId>com.deepagents</groupId>
  <artifactId>langchain4j-deepagents</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

You still need `langchain4j` + your provider (e.g. `langchain4j-open-ai`) in your application, or rely on transitive deps from this artifact if you align versions.

---

## Core entry points

| Type | Purpose |
|------|---------|
| `DeepAgent.create(DeepAgentConfig)` | Recommended: full config in one object |
| `DeepAgent.create(ChatModel, Path, List<Path>, List<SubAgentDefinition>, …)` | Shortcut overloads without `DeepAgentConfig` |
| `DeepAgent.builder()` | Fluent builder → `build()` returns `Orchestrator` |
| `DeepAgent.Orchestrator` | `String chat(String memoryId, String userMessage)` |

The orchestrator is a LangChain4j **`AiServices`** facade: one **`@UserMessage`** per turn, **`@MemoryId`** for session-scoped chat memory (`MessageWindowChatMemory`).

---

## Configuration: `DeepAgentConfig`

Build with `DeepAgentConfig.builder()`. You must set **exactly one** of:

- `openAi(OpenAiChatModelConfig)` — minimal key + model name → `toChatModel()`
- `chatModel(ChatModel)` — any provider (Anthropic, local, custom base URL, etc.)

### `DeepAgentConfig.Builder` methods

| Method | Description |
|--------|-------------|
| `workspace(Path)` | **Required.** Sandbox root for file tools; created if missing. |
| `openAi(OpenAiChatModelConfig)` | Mutually exclusive with `chatModel`. |
| `chatModel(ChatModel)` | Mutually exclusive with `openAi`. |
| `addSkillSourceRoot(Path)` / `skillSourceRoots(List<Path>)` | Directories under workspace whose subfolders contain `SKILL.md`; catalog appended to system prompts. |
| `addSubAgent(SubAgentDefinition)` / `subAgents(List<…>)` | Extra specialists (see below). `general-purpose` is always registered for you. |
| `instructions(String)` | Prepended **before** the default deep-agent base prompt (and before the skills section). |
| `chatMemoryMaxMessages(int)` | Default `48`. |
| `maxSequentialToolInvocations(int)` | Default `35`. |
| `additionalTools(Map<ToolSpecification, ToolExecutor>)` | Merged into **orchestrator and every sub-agent** (after built-in file tools where enabled). |
| `toolInvocationLogMode(ToolInvocationLogMode)` | `NONE`, `INFO`, or `DEBUG`. |
| `flowListener(DeepAgentFlowListener)` | Custom tracing callbacks. |
| `recordFlowTraceToStderr(boolean)` | If `true`, installs `DeepAgentFlowRecorder`; **mutually exclusive** with `flowListener`. After `chat`, use `config.stderrFlowRecorder().ifPresent(DeepAgentFlowRecorder::printTimelineToStderr)`. |

### `OpenAiChatModelConfig`

| API | Description |
|-----|-------------|
| `OpenAiChatModelConfig.of(apiKey, modelName)` | Record constructor validation. |
| `toChatModel()` | `OpenAiChatModel` with key + model only. |
| `fromRequiredEnvironment()` | Reads `OPENAI_API_KEY` (required) and `OPENAI_MODEL` (optional, default `gpt-4o`). |

For **custom base URL**, temperature, or other OpenAI options, build `OpenAiChatModel` yourself and pass `DeepAgentConfig.builder().chatModel(model)`.

---

## Sub-agents: `DeepAgent.SubAgent` → `SubAgentDefinition`

```java
SubAgentDefinition analyst =
    DeepAgent.SubAgent.builder()
        .name("data-analyst")
        .description("Analyzes CSV and text files in the workspace.") // shown in `task` tool text
        .prompt("You are a specialist. Return concise findings for the orchestrator.")
        .builtInFileTools(true)   // default: same list_dir/read_file/write_file/edit_file as orchestrator
        .build();
```

| Field | Role |
|-------|------|
| `name` | Must match `subAgentType` in the `task` tool; cannot be `general-purpose` (reserved). |
| `description` | Embedded in the orchestrator’s `task` tool description. |
| `prompt` | Sub-agent system instructions (does **not** inherit the orchestrator system prompt). |
| `builtInFileTools` | If `false`, sub-agent has no harness file tools (text-only). |
| `tools(Map)` / `addTool(spec, executor)` | Per-sub-agent tools merged **after** shared `additionalTools`. |

Sub-agents **do not** get the `task` tool (no recursion).

---

## Built-in tools (orchestrator)

| Tool | Role |
|------|------|
| `write_todos` | Replace the **entire** todo list for this `memoryId`. |
| `list_dir` | List a path under the workspace. |
| `read_file` | Read a text file under the workspace (paths relative to sandbox root). |
| `write_file` | Write/overwrite a file. |
| `edit_file` | Apply a single substring replacement in a file. |
| `task` | Run a named sub-agent with a natural-language `description`. |

### `write_todos` — JSON arguments

```json
{
  "todos": [
    { "content": "Fix average calculation", "status": "in_progress" },
    { "content": "Run verification read", "status": "pending" }
  ]
}
```

`status`: `pending`, `in_progress`, or `completed` (snake_case; uppercase variants also accepted). Each call **replaces** the full list for that session.

### `task` — JSON arguments

```json
{
  "description": "Read sample/Foo.java and list correctness issues only.",
  "subAgentType": "bug-finder"
}
```

`subAgentType` must be one of the registered agents (always includes `general-purpose`, plus your `SubAgentDefinition` names).

### File tools — typical shapes

- `read_file`: `{ "path": "relative/path/from/workspace/root.txt" }`
- `write_file`: `{ "path": "…", "content": "…" }`
- `edit_file`: `{ "path": "…", "old_string": "…", "new_string": "…", "replace_all": false }` (`replace_all` optional, default `false`)

Exact schemas match the `ToolSpecification` definitions in `FileToolFactory` / `TodoToolFactory` / `TaskToolFactory`.

---

## Example: minimal OpenAI + workspace + one sub-agent

```java
import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.config.DeepAgentConfig;
import com.deepagents.langchain4j.config.OpenAiChatModelConfig;
import com.deepagents.langchain4j.subagents.SubAgentDefinition;

import java.nio.file.Path;
import java.util.List;

Path workspace = Path.of("/tmp/my-agent-workspace");

OpenAiChatModelConfig modelCfg = OpenAiChatModelConfig.fromRequiredEnvironment();

SubAgentDefinition reviewer =
    DeepAgent.SubAgent.builder()
        .name("reviewer")
        .description("Reviews text files for clarity and bugs.")
        .prompt("You only report findings; the orchestrator applies edits.")
        .builtInFileTools(true)
        .build();

DeepAgentConfig config =
    DeepAgentConfig.builder()
        .workspace(workspace)
        .openAi(modelCfg)
        .subAgents(List.of(reviewer))
        .build();

DeepAgent.Orchestrator agent = DeepAgent.create(config);
String reply = agent.chat("session-1", "Summarize notes/draft.md and ask reviewer to check tone.");
```

## Example: custom `ChatModel` (any provider)

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

ChatModel model = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-4o-mini")
    .baseUrl("https://api.example.com/v1") // if needed
    .build();

DeepAgentConfig config =
    DeepAgentConfig.builder()
        .workspace(Path.of("/tmp/ws"))
        .chatModel(model)
        .instructions("Always prefer read_file before editing.")
        .build();

DeepAgent.Orchestrator agent = DeepAgent.create(config);
```

## Example: `DeepAgent.builder()` (same wiring, fluent)

```java
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;

SubAgentDefinition reviewer =
    DeepAgent.SubAgent.builder()
        .name("reviewer")
        .description("Reviews workspace files.")
        .prompt("Return findings only.")
        .build();

DeepAgent.Orchestrator agent =
    DeepAgent.builder()
        .workspace(Path.of("/tmp/ws"))
        .openAi(OpenAiChatModelConfig.of(System.getenv("OPENAI_API_KEY"), "gpt-4o"))
        .addSubAgent(reviewer)
        .instructions("Optional orchestrator prefix…")
        .toolInvocationLogMode(ToolInvocationLogMode.NONE)
        .build();
```

## Example: flow timeline to stderr

```java
DeepAgentConfig config =
    DeepAgentConfig.builder()
        .workspace(workspace)
        .openAi(modelCfg)
        .recordFlowTraceToStderr(true)
        .build();

DeepAgent.Orchestrator agent = DeepAgent.create(config);
System.out.println(agent.chat("demo", "Your task…"));
config.stderrFlowRecorder().ifPresent(com.deepagents.langchain4j.flow.DeepAgentFlowRecorder::printTimelineToStderr);
```

---

## System prompts

- **Base orchestrator text**: `Prompts.ORCHESTRATOR_SYSTEM` ← `agent-prompts/base_agent_prompt.txt` (same idea as Python `BASE_AGENT_PROMPT` when `system_prompt=None`).
- **Your `instructions()`**: prepended to that base (then skills catalog if configured).
- **Sub-agents**: get their own `prompt` plus `Prompts.NON_INTERACTIVE_SUBAGENT` and the same skills section as the orchestrator when skills are enabled.

Todo behavior is driven mainly by the **`write_todos` tool description** (`write_todos.txt`), not by the base prompt.

---

## Build and test

```bash
cd langchain4j-deepagents
mvn -q test
```

---

## Run bundled demos

Each class under `com.deepagents.langchain4j.demos` is a **`main`** with a fixed workspace (usually `./workspace-demo/...` relative to the JVM working directory — project root when using Maven).

| Main class | Focus |
|------------|--------|
| `BrokenStatsJavaCodeReviewTodosFilesAndSubagentsDemo` | Default `exec:java`. Multi-file repair: `sample/BrokenStats.java` + `sample/StatsSummary.java`, **`bug-finder`** + **`performance-reviewer`**, todos, orchestrator-only edits. |
| `SkillsMarkdownCatalogProgressiveDisclosureDemo` | Skills under `workspace-demo/demos/skills-sample/`; model `read_file`s a `SKILL.md`. |
| `ResearchGatherVerifySocialDraftsDemo` | Sub-agents for research; skills for social draft playbooks under `workspace-demo/demos/research-verify-example/`. |

```bash
export OPENAI_API_KEY=sk-...

mvn -q compile exec:java

mvn -q compile exec:java \
  -Dexec.mainClass=com.deepagents.langchain4j.demos.SkillsMarkdownCatalogProgressiveDisclosureDemo

mvn -q compile exec:java \
  -Dexec.mainClass=com.deepagents.langchain4j.demos.ResearchGatherVerifySocialDraftsDemo
```

---

## Package map

| Package | Contents |
|---------|----------|
| `com.deepagents.langchain4j` | `DeepAgent`, `Prompts` |
| `com.deepagents.langchain4j.config` | `DeepAgentConfig`, `OpenAiChatModelConfig` |
| `com.deepagents.langchain4j.subagents` | `SubAgentDefinition`, runtime |
| `com.deepagents.langchain4j.files` | `WorkspaceFileOperations`, `FileToolFactory` |
| `com.deepagents.langchain4j.todos` | `TodoToolFactory`, models |
| `com.deepagents.langchain4j.task` | `TaskToolFactory`, descriptions |
| `com.deepagents.langchain4j.skills` | Scan + format skills catalog |
| `com.deepagents.langchain4j.flow` | `DeepAgentFlowListener`, `DeepAgentFlowRecorder` |
| `com.deepagents.langchain4j.logging` | `ToolInvocationLogMode` |
| `com.deepagents.langchain4j.demos` | Runnable examples |

---

## Limits and differences vs Python deepagents

- No LangGraph **checkpointing** — `MessageWindowChatMemory` + tool loop only.
- LangChain’s middleware historically rejects **multiple `write_todos` in one assistant message**; this library does not add a separate enforcement layer (policy is in the tool description).
- No built-in shell **`execute`**, glob, grep, or auto-summarization (extend via `additionalTools` / LangChain4j APIs).
- Workspace paths for `read_file` / `write_file` / `edit_file` are **relative to the configured workspace root** (the ported prompt text may mention “absolute” paths; the Java sandbox uses the root you pass in).

---

## License

Apache 2.0 — verify compatibility when embedding; attribute LangChain / upstream projects for ported prompt text where appropriate.
