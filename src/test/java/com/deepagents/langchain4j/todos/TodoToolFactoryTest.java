package com.deepagents.langchain4j.todos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoToolFactoryTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void memoryKey_nullBecomesDefault() {
        assertEquals("_default", TodoToolFactory.memoryKey(null));
    }

    @Test
    void writeTodos_replacesFullList() throws Exception {
        Map<ToolSpecification, ToolExecutor> tools = TodoToolFactory.buildPerMemoryId();
        ToolSpecification spec =
                tools.keySet().stream().filter(t -> "write_todos".equals(t.name())).findFirst().orElseThrow();
        ToolExecutor exec = tools.get(spec);

        ObjectNode args = JSON.createObjectNode();
        ArrayNode todos = args.putArray("todos");
        ObjectNode a = todos.addObject();
        a.put("content", "First");
        a.put("status", "in_progress");
        ObjectNode b = todos.addObject();
        b.put("content", "Second");
        b.put("status", "pending");

        String out =
                exec.execute(
                        ToolExecutionRequest.builder().id("1").name("write_todos").arguments(args.toString()).build(),
                        "session-1");
        assertTrue(out.contains("Updated todo list"), out);
        assertTrue(out.contains("First"), out);
        assertTrue(out.contains("Second"), out);
        assertTrue(out.contains("IN_PROGRESS"), out);

        ObjectNode args2 = JSON.createObjectNode();
        ArrayNode todos2 = args2.putArray("todos");
        ObjectNode c = todos2.addObject();
        c.put("content", "Only");
        c.put("status", "completed");

        String out2 =
                exec.execute(
                        ToolExecutionRequest.builder().id("2").name("write_todos").arguments(args2.toString()).build(),
                        "session-1");
        assertTrue(out2.contains("Only"), out2);
        assertTrue(!out2.contains("First"), "prior task should be gone after full replace");
    }

    @Test
    void buildPerMemoryId_isolatesTodosByMemoryId() throws Exception {
        Map<ToolSpecification, ToolExecutor> tools = TodoToolFactory.buildPerMemoryId();
        ToolSpecification spec =
                tools.keySet().stream().filter(t -> "write_todos".equals(t.name())).findFirst().orElseThrow();
        ToolExecutor exec = tools.get(spec);

        ObjectNode argsA = JSON.createObjectNode();
        ArrayNode arrA = argsA.putArray("todos");
        arrA.addObject().put("content", "user A task").put("status", "pending");

        exec.execute(
                ToolExecutionRequest.builder().id("1").name("write_todos").arguments(argsA.toString()).build(),
                "session-a");

        ObjectNode argsB = JSON.createObjectNode();
        ArrayNode arrB = argsB.putArray("todos");
        arrB.addObject().put("content", "user B task").put("status", "pending");

        exec.execute(
                ToolExecutionRequest.builder().id("2").name("write_todos").arguments(argsB.toString()).build(),
                "session-b");

        ObjectNode refreshA = JSON.createObjectNode();
        refreshA.putArray("todos").addObject().put("content", "user A task").put("status", "completed");
        String listA =
                exec.execute(
                        ToolExecutionRequest.builder().id("3").name("write_todos").arguments(refreshA.toString()).build(),
                        "session-a");
        ObjectNode refreshB = JSON.createObjectNode();
        refreshB.putArray("todos").addObject().put("content", "user B task").put("status", "in_progress");
        String listB =
                exec.execute(
                        ToolExecutionRequest.builder().id("4").name("write_todos").arguments(refreshB.toString()).build(),
                        "session-b");

        assertTrue(listA.contains("user A task"), listA);
        assertTrue(listB.contains("user B task"), listB);
        assertTrue(!listA.contains("user B task"), listA);
        assertTrue(!listB.contains("user A task"), listB);
    }

    @Test
    void buildSharedStore_oneUnderlyingList_allMemoryIdsShareReplacements() throws Exception {
        var store = new TodoStore();
        Map<ToolSpecification, ToolExecutor> tools = TodoToolFactory.build(store);
        ToolSpecification spec =
                tools.keySet().stream().filter(t -> "write_todos".equals(t.name())).findFirst().orElseThrow();
        ToolExecutor exec = tools.get(spec);

        ObjectNode first = JSON.createObjectNode();
        first.putArray("todos").addObject().put("content", "from-session-1").put("status", "pending");
        exec.execute(
                ToolExecutionRequest.builder().id("1").name("write_todos").arguments(first.toString()).build(),
                "session-1");

        ObjectNode second = JSON.createObjectNode();
        second.putArray("todos").addObject().put("content", "from-session-2").put("status", "in_progress");
        String afterReplace =
                exec.execute(
                        ToolExecutionRequest.builder().id("2").name("write_todos").arguments(second.toString()).build(),
                        "session-2");

        assertTrue(afterReplace.contains("from-session-2"), afterReplace);
        assertTrue(!afterReplace.contains("from-session-1"), "shared store: second write replaces entire list");

        ObjectNode clear = JSON.createObjectNode();
        clear.putArray("todos");
        String cleared =
                exec.execute(
                        ToolExecutionRequest.builder().id("3").name("write_todos").arguments(clear.toString()).build(),
                        "session-x");
        assertTrue(cleared.contains("no todos"), cleared);
    }

    @Test
    void todoStatus_parseApi_acceptsSnakeCaseAndEnumNames() {
        assertEquals(TodoStatus.IN_PROGRESS, TodoStatus.parseApi("in_progress"));
        assertEquals(TodoStatus.PENDING, TodoStatus.parseApi("PENDING"));
        assertThrows(IllegalArgumentException.class, () -> TodoStatus.parseApi("bogus"));
    }
}
