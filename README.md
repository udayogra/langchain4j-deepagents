# langchain4j-deepagents

A **small agent harness for Java**—opinionated and ready to run—so you are not wiring prompts, tool schemas, and context management yourself. You get a working **tool-using** orchestrator first, then customize workspace, model, sub-agents, and extras.

The design follows the same goals as **[LangChain deepagents](https://github.com/langchain-ai/deepagents)** (Python): *“Agent harness built with LangChain and LangGraph… planning tool, filesystem backend, and the ability to spawn subagents.”* This repo is a **LangChain4j-only** take on that shape (**Java 17+**, no Spring, no LangGraph dependency).

**What’s included** (aligned with the upstream harness idea):

- **Planning** — `write_todos` for task breakdown and progress  
- **Filesystem** — `list_dir`, `read_file`, `write_file`, `edit_file` against a sandbox workspace  
- **Sub-agents** — `task` to delegate work with an isolated sub-prompt (and optional tools)  
- **Optional skills** — folders with `SKILL.md`, surfaced as a catalog; full text via `read_file`  
- **Optional agent memory** — markdown files (e.g. `AGENTS.md`) loaded into the system prompt; see [Agent memory](#agent-memory-agentsmd)  
- **Defaults** — orchestrator + sub-agent prompts aimed at using those tools effectively; bounded chat memory via LangChain4j  

---

## Built-in tools (orchestrator)

| Tool | What it does |
|------|----------------|
| `write_todos` | Lets the model set or refresh its task checklist for the current chat. |
| `list_dir` | List files and folders in the workspace. |
| `read_file` | Read a file from the workspace. |
| `write_file` | Create or overwrite a file. |
| `edit_file` | Change part of a file (find and replace). |
| `task` | Hand off work to a specialist sub-agent you configured. |

---

## Minimal usage

1. Build a **`DeepAgentConfig`** (workspace + exactly one of **`chatModel(...)`** or **`openAi(...)`** — see below).
2. Call **`DeepAgent.create(config)`** to get **`DeepAgent.Orchestrator`**.
3. Call **`agent.chat(memoryId, userMessage)`** — `memoryId` scopes chat memory and todos; the workspace directory is still shared unless you choose different roots per tenant in your app.

```java
import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.config.DeepAgentConfig;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.nio.file.Path;

Path workspace = Path.of("/tmp/my-agent-workspace");

ChatModel model =
        OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o")
                .build();

DeepAgentConfig config =
        DeepAgentConfig.builder()
                .workspace(workspace)
                .chatModel(model)
                .build();

DeepAgent.Orchestrator agent = DeepAgent.create(config);
String reply = agent.chat("session-1", "List the workspace root, then summarize what you see.");
```

Set **`OPENAI_API_KEY`** in the environment (or substitute your own key source). To wire the model from env vars without calling **`System.getenv`** yourself, see [**`OpenAiChatModelConfig`**](#openaichatmodelconfig-optional-helper) below.

The orchestrator is a LangChain4j **`AiServices`** facade: one **`@UserMessage`** per turn, **`@MemoryId`** for **`MessageWindowChatMemory`**.

---

## `DeepAgentConfig` — what each option does

You must set **exactly one** of:

- **`chatModel(ChatModel)`** — any LangChain4j chat model (OpenAI, Anthropic, local, custom base URL, etc.). This is what the [minimal example](#minimal-usage) uses.
- **`openAi(OpenAiChatModelConfig)`** — shorthand: key + model name; the library calls **`toChatModel()`** for you.

| Builder method | Purpose |
|----------------|---------|
| **`workspace(Path)`** | **Required.** The **root folder** for all built-in file tools (`list_dir`, `read_file`, `write_file`, `edit_file`). Paths the model passes to those tools are **relative to this directory**, and the implementation keeps access **inside** that tree—so this is your **safety boundary** for disk access.<br><br>Think of it as the agent’s **project directory** (source files, generated output, skill trees under `skillSourceRoots`, etc.). If the path does not exist yet, it is **created**.<br><br>**Note:** every chat session (`memoryId`) uses the **same** workspace unless you point different configs at different paths; split workspaces when you need **per-tenant or per-job** file isolation. |
| **`instructions(String)`** | Optional text **prepended** before the library’s default orchestrator system message (and before any skills catalog). |
| **`addSkillSourceRoot` / `skillSourceRoots`** | Optional. Each root is a directory **inside** the workspace; immediate subfolders that contain `SKILL.md` are listed in the system prompt (see [Skills](#skills)). |
| **`addMemorySource` / `memorySources`** | Optional. Markdown files **inside** the workspace whose contents are injected as agent memory (see [Agent memory](#agent-memory-agentsmd)). |
| **`addSubAgent` / `subAgents`** | Optional specialists exposed as `task` targets (see [Sub-agents](#sub-agents)). `general-purpose` is always registered. |
| **`chatMemoryMaxMessages(int)`** | Orchestrator window size; default **48**. |
| **`maxSequentialToolInvocations(int)`** | Cap on tool steps per model turn; default **35**. |
| **`additionalTools(Map<…>)`** | Extra tools merged into the **orchestrator and every sub-agent** (after built-in file tools where enabled). Use `SubAgentDefinition` for tools that belong to one specialist only. |
| **`toolInvocationLogMode`** | `NONE`, `INFO`, or `DEBUG` — logs each tool call for orchestrator and sub-agents (see [Logging tool calls](#logging-tool-calls)). Default **INFO**. |
| **`flowListener`** | Custom callbacks for assembled prompt, user turns, tool calls, and `task` delegation (see [Flow tracing](#flow-tracing)). |
| **`recordFlowTraceToStderr(true)`** | Installs a **`DeepAgentFlowRecorder`** as the flow listener. **Mutually exclusive** with `flowListener`. After `chat`, use **`config.stderrFlowRecorder()`** to print a timeline. |

### `OpenAiChatModelConfig` (optional helper)

Small record for **OpenAI-compatible** setups when you only need an API key and a model id. It is **not** required if you already build a **`ChatModel`** yourself (as in the minimal example).

| API | What it does |
|-----|----------------|
| `OpenAiChatModelConfig.of(apiKey, modelName)` | Validates non-blank key and model name. |
| `toChatModel()` | Returns **`OpenAiChatModel.builder().apiKey(...).modelName(...).build()`**. |
| `fromRequiredEnvironment()` | Reads the process environment (see below). |

**`fromRequiredEnvironment()`** looks at:

| Variable | Required? | If missing / empty |
|----------|-----------|---------------------|
| **`OPENAI_API_KEY`** | Yes | Throws **`IllegalStateException`** with a short message telling you to set it (and optionally `OPENAI_MODEL`). |
| **`OPENAI_MODEL`** | No | Defaults to **`gpt-4o`**. |

Example (same outcome as building `OpenAiChatModel` by hand, but driven by env):

```java
import com.deepagents.langchain4j.config.OpenAiChatModelConfig;

OpenAiChatModelConfig cfg = OpenAiChatModelConfig.fromRequiredEnvironment();
DeepAgentConfig config =
        DeepAgentConfig.builder()
                .workspace(workspace)
                .openAi(cfg)
                .build();
```

For temperature, **`baseUrl`**, timeouts, or non-OpenAI providers, build the right **`ChatModel`** in your app and use **`chatModel(...)`**.

---

## Skills

Skills are **folders** (direct children of each configured skill root) that contain a **`SKILL.md`**. The library scans those roots, builds a **compact catalog** (names + paths), and appends it to the orchestrator and sub-agent system prompts so the model can **`read_file`** the full skill when needed (“progressive disclosure”).

**Convention:** each path in `skillSourceRoots` must resolve **inside** `workspace`.

Example (matches **`SkillsMarkdownCatalogProgressiveDisclosureDemo`**): workspace `./workspace-demo`, skill root `workspace-demo/demos/skills-sample`, user asks the model to `read_file` a specific `SKILL.md` after seeing the catalog.

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

Path workspace = Path.of(System.getProperty("user.dir"), "workspace-demo");
List<Path> skillRoots = List.of(workspace.resolve("demos/skills-sample"));

ChatModel model =
        OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o")
                .build();

DeepAgentConfig config =
        DeepAgentConfig.builder()
                .workspace(workspace)
                .chatModel(model)
                .skillSourceRoots(skillRoots)
                .build();

DeepAgent.Orchestrator agent = DeepAgent.create(config);
```

### Agent memory (`AGENTS.md`)

**What it is:** Memory is extra context you keep in markdown files (often called [AGENTS.md](https://agents.md/)). It is project- or team-specific instructions—how to build, style rules, architecture notes—so the agent always sees them. Skills are optional playbooks you open when needed; memory is **always included** in the system prompt when you configure it.

**How you specify it:** Pass one or more file paths with **`addMemorySource(Path)`** or **`memorySources(List)`** on **`DeepAgentConfig`**. In this library, each path must live **inside your `workspace`** (relative to it, or absolute but still under that root) so the same **`edit_file`** / **`read_file`** sandbox can update it. Paths are read **in order**; contents are **combined** in that order (later files appear after earlier ones). If a path is missing, it is skipped. If you listed sources but nothing could be read, the prompt says there is no memory yet.

**Loading and changing:** Content is read when you call **`DeepAgent.create`**. It is injected into the main agent and sub-agents, together with short guidelines (same idea as [deepagentsjs `memory.ts`](https://github.com/langchain-ai/deepagentsjs/blob/main/libs/deepagents/src/middleware/memory.ts)) on when to update memory using **`edit_file`**. Memory is **not frozen forever**: you can change it whenever you need to—edit the markdown yourself, let the model update files with **`edit_file`** when that fits, or adjust **`memorySources`** and rebuild. The text baked into a **running** `Orchestrator`’s system message does **not** auto-refresh every turn; after a disk change, call **`DeepAgent.create`** again (or start a new process) so the next session picks up the latest files.

Runnable example: **`AgentMemoryAgentsMdDemo`** (loads `workspace-demo/demos/agent-memory-sample/AGENTS.md`; see [Run bundled demos](#run-bundled-demos)).

```java
DeepAgentConfig.builder()
        .workspace(workspace)
        .chatModel(model)
        .addMemorySource(Path.of("AGENTS.md"))
        .addMemorySource(Path.of(".deepagents/AGENTS.md"))
        .build();
```

---

## Sub-agents

The orchestrator can call **`task`** to run a sub-agent. With **`builtInFileTools(true)`** (the default), a sub-agent gets the same **workspace file** tools as the main agent, plus any shared **`additionalTools`** and its own **`extraTools`**. Only the orchestrator has **`write_todos`** and **`task`**; sub-agents cannot delegate again. A built-in **`general-purpose`** sub-agent is always available for simple handoffs.

Define specialists with **`DeepAgent.SubAgent.builder()`** → **`SubAgentDefinition`**, then pass them with **`subAgents(...)`**.

```java
import com.deepagents.langchain4j.subagents.SubAgentDefinition;

// Same `workspace` and `ChatModel model` as in Minimal usage.

SubAgentDefinition reviewer =
        DeepAgent.SubAgent.builder()
                .name("reviewer")
                .description("Reviews text files for clarity; return findings only.")
                .prompt("You are a reviewer. Do not edit files unless asked.")
                .builtInFileTools(true)
                .build();

DeepAgentConfig config =
        DeepAgentConfig.builder()
                .workspace(workspace)
                .chatModel(model)
                .subAgents(List.of(reviewer))
                .build();
```

| Field | Role |
|-------|------|
| `name` | Used as `subagent_type` in the `task` tool (deepagentsjs / LangChain); cannot be `general-purpose`. |
| `description` | Shown in the orchestrator’s `task` tool so the model knows when to delegate. |
| `prompt` | Sub-agent system message; **does not** inherit the orchestrator system prompt. |
| `builtInFileTools` | Default `true` — same file tools as the orchestrator; set `false` for text-only. |
| `extraTools` | Per-sub-agent tools merged **after** shared `additionalTools`. |

### `task` tool — JSON shape

```json
{
  "description": "Read sample/Foo.java and list correctness issues only.",
  "subagent_type": "reviewer"
}
```

`subagent_type` must be `general-purpose` or one of your definition names. The executor still accepts legacy `subAgentType` if the model emits it.

### `write_todos` — JSON shape

```json
{
  "todos": [
    { "content": "Fix average calculation", "status": "in_progress" },
    { "content": "Verify with read_file", "status": "pending" }
  ]
}
```

`status`: `pending`, `in_progress`, or `completed` (snake_case; some uppercase variants accepted). Each call **replaces** the full list for that `memoryId`.

A fuller multi-sub-agent workflow (orchestrator-only edits, todos, two specialists) lives in **`BrokenStatsJavaCodeReviewTodosFilesAndSubagentsDemo`**.

---

## Logging tool calls

Set **`toolInvocationLogMode`** to reduce noise or increase detail:

```java
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;

// Same `workspace` and `ChatModel model` as in Minimal usage.

DeepAgentConfig config =
        DeepAgentConfig.builder()
                .workspace(workspace)
                .chatModel(model)
                .toolInvocationLogMode(ToolInvocationLogMode.DEBUG)
                .build();
```

---

## Flow tracing

To print a **chronological timeline** (system prompt ready, user message, tools, sub-agent tasks) to stderr after a run:

```java
import com.deepagents.langchain4j.flow.DeepAgentFlowRecorder;

// Same `workspace` and `ChatModel model` as in Minimal usage.

DeepAgentConfig config =
        DeepAgentConfig.builder()
                .workspace(workspace)
                .chatModel(model)
                .recordFlowTraceToStderr(true)
                .build();

DeepAgent.Orchestrator agent = DeepAgent.create(config);
System.out.println(agent.chat("demo", "Your task…"));
config.stderrFlowRecorder().ifPresent(DeepAgentFlowRecorder::printTimelineToStderr);
```

For custom handling, pass your own **`DeepAgentFlowListener`** with **`flowListener(...)`** instead (do not combine with `recordFlowTraceToStderr(true)`).

---

## Requirements

- **Java 17**
- **LangChain4j** (see this project’s `pom.xml`, e.g. `1.9.1`)
- A **chat model with tool calling**

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

Align `langchain4j` and provider versions with your application.

---

## Build and test

```bash
cd langchain4j-deepagents
mvn -q test
```

---

## Run bundled demos

Runnable `main` classes under **`com.deepagents.langchain4j.demos`** use **`./workspace-demo/`** relative to the JVM working directory (project root when using Maven).

| Main class | Focus |
|------------|--------|
| `BrokenStatsJavaCodeReviewTodosFilesAndSubagentsDemo` | Default `exec:java`. Todos, files, **`bug-finder`** + **`performance-reviewer`**. |
| `SkillsMarkdownCatalogProgressiveDisclosureDemo` | Skills under `workspace-demo/demos/skills-sample/`. |
| `ResearchGatherVerifySocialDraftsDemo` | Research-style sub-agents + skills under `workspace-demo/demos/research-verify-example/`. |
| `AgentMemoryAgentsMdDemo` | Agent memory from `workspace-demo/demos/agent-memory-sample/AGENTS.md`. |

**Which `main` ran?** Plain `mvn exec:java` uses the POM property default (`BrokenStatsJavaCodeReviewTodosFilesAndSubagentsDemo`). The plugin config uses **`<mainClass>${exec.mainClass}</mainClass>`** so `-Dexec.mainClass=...` works (a **literal** `<mainClass>` in the exec plugin XML is **not** overridden by `-Dexec.mainClass` in exec-maven-plugin 3.x). Keep `-Dexec.mainClass` on **one line** so the shell does not split the command. Each demo prints `>>> Running fully.qualified.ClassName` to **stderr** at startup.

```bash
export OPENAI_API_KEY=sk-...

mvn -q compile exec:java

mvn -q compile exec:java -Dexec.mainClass=com.deepagents.langchain4j.demos.SkillsMarkdownCatalogProgressiveDisclosureDemo

mvn -q compile exec:java -Dexec.mainClass=com.deepagents.langchain4j.demos.ResearchGatherVerifySocialDraftsDemo

mvn -q compile exec:java -Dexec.mainClass=com.deepagents.langchain4j.demos.AgentMemoryAgentsMdDemo
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
| `com.deepagents.langchain4j.task` | `TaskToolFactory` |
| `com.deepagents.langchain4j.skills` | Scan + format skills catalog |
| `com.deepagents.langchain4j.memory` | `AgentMemoryLoader` (AGENTS.md-style injection) |
| `com.deepagents.langchain4j.flow` | `DeepAgentFlowListener`, `DeepAgentFlowRecorder` |
| `com.deepagents.langchain4j.logging` | `ToolInvocationLogMode` |
| `com.deepagents.langchain4j.demos` | Runnable examples |

Long tool descriptions and base prompts ship as classpath resources under **`agent-prompts/`** (loaded via `Prompts` / factories).

---

## Limits and differences vs Python deepagents

- No LangGraph **checkpointing** — `MessageWindowChatMemory` + tool loop only.
- LangChain’s middleware historically rejects **multiple `write_todos` in one assistant message**; this library does not add a separate enforcement layer (policy is in the tool description).
- No built-in shell **`execute`**, glob, or grep (extend via `additionalTools` / LangChain4j APIs).
- No **summarization** of chat history (no automatic “compress the thread” step). Context is capped only by **`MessageWindowChatMemory`** and **`chatMemoryMaxMessages`**; add summarization yourself if you need it.
- File tool paths are **relative to the configured workspace root**.

---

## License

Apache 2.0 — verify compatibility when embedding; attribute LangChain / upstream projects for ported prompt text where appropriate.
