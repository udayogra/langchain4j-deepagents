package com.deepagents.langchain4j.todos;

import com.deepagents.langchain4j.ResourceTexts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Single {@code write_todos} tool (full list replacement per call), aligned with LangChain JS
 * {@code todoListMiddleware} ({@code WRITE_TODOS_DESCRIPTION}) / Python {@code write_todos}.
 *
 * <p>For multi-user apps, use {@link #buildPerMemoryId()} or {@link #build(Function)} so each {@link
 * dev.langchain4j.service.MemoryId} gets its own {@link TodoStore}.
 */
public final class TodoToolFactory {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TodoToolFactory() {}

    public static Map<ToolSpecification, ToolExecutor> buildPerMemoryId() {
        ConcurrentHashMap<String, TodoStore> bySession = new ConcurrentHashMap<>();
        return build(memoryId -> bySession.computeIfAbsent(memoryKey(memoryId), k -> new TodoStore()));
    }

    static String memoryKey(Object memoryId) {
        return memoryId == null ? "_default" : String.valueOf(memoryId);
    }

    public static Map<ToolSpecification, ToolExecutor> build(TodoStore sharedStore) {
        return build(mid -> sharedStore);
    }

    /**
     * Tool executor resolves the store via {@code storeForMemory.apply(memoryId)} on each invocation.
     */
    public static Map<ToolSpecification, ToolExecutor> build(Function<Object, TodoStore> storeForMemory) {
        String writeTodos = ResourceTexts.load("write_todos.txt");

        JsonObjectSchema todoElementSchema =
                JsonObjectSchema.builder()
                        .addStringProperty("content", "Task description")
                        .addStringProperty(
                                "status",
                                "pending, in_progress, or completed (snake_case, LangChain); or PENDING, IN_PROGRESS, COMPLETED")
                        .required("content", "status")
                        .build();

        JsonArraySchema todosArraySchema =
                JsonArraySchema.builder()
                        .description("Complete new todo list; replaces any existing todos. Use [] to clear.")
                        .items(todoElementSchema)
                        .build();

        ToolSpecification spec =
                ToolSpecification.builder()
                        .name("write_todos")
                        .description(
                                writeTodos
                                        + """

                                        ---
                                        Tool implementation: JSON object with a "todos" array. Each element is
                                        { "content": "<text>", "status": "<pending|in_progress|completed>" }.
                                        This call replaces the entire list (same semantics as LangChain write_todos).
                                        Do not issue multiple write_todos tool calls in parallel in the same assistant turn.""")
                        .parameters(
                                JsonObjectSchema.builder()
                                        .addProperty("todos", todosArraySchema)
                                        .required("todos")
                                        .build())
                        .build();

        ToolExecutor executor =
                (ToolExecutionRequest request, Object memoryId) -> {
                    try {
                        TodoStore store = storeForMemory.apply(memoryId);
                        JsonNode root = JSON.readTree(request.arguments());
                        if (!root.has("todos") || !root.get("todos").isArray()) {
                            return "Error: missing or invalid \"todos\" array";
                        }
                        JsonNode arr = root.get("todos");
                        List<TodoItem> parsed = new ArrayList<>();
                        for (JsonNode el : arr) {
                            if (!el.isObject()) {
                                return "Error: each todo must be a JSON object with content and status";
                            }
                            String content = el.path("content").asText("").trim();
                            if (content.isBlank()) {
                                return "Error: each todo must have non-blank content";
                            }
                            String st = el.path("status").asText("");
                            TodoStatus status = TodoStatus.parseApi(st);
                            parsed.add(new TodoItem(0, content, status));
                        }
                        store.replaceAll(parsed);
                        return "Updated todo list to:\n"
                                + store.formatForModel()
                                + """

                                ---
                                Re-call write_todos after each meaningful step (complete → mark completed, start next → mark \
                                in_progress, reprioritize → edit list). Each call must include the full list. Do not stop \
                                updating until every item is completed or the plan is abandoned.""";
                    } catch (IllegalArgumentException e) {
                        return "Error: " + e.getMessage();
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                };

        return Map.of(spec, executor);
    }

    /** Merge helper for harness. */
    public static void putAll(Map<ToolSpecification, ToolExecutor> target, Map<ToolSpecification, ToolExecutor> source) {
        for (Map.Entry<ToolSpecification, ToolExecutor> e : source.entrySet()) {
            if (target.put(e.getKey(), e.getValue()) != null) {
                throw new IllegalStateException("Duplicate tool: " + e.getKey().name());
            }
        }
    }
}
