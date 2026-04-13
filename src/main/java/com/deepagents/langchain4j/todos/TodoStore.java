package com.deepagents.langchain4j.todos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory todo list for one orchestrator session (not persisted). Updated only via {@link #replaceAll} (full list
 * replacement, LangChain {@code write_todos} semantics).
 */
public final class TodoStore {

    private final List<TodoItem> items = new ArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Replaces the entire list. Assigns stable display ids {@code 1..n} in order (ids are not part of the tool payload).
     */
    public synchronized void replaceAll(List<TodoItem> newTodos) {
        items.clear();
        int id = 1;
        for (TodoItem t : newTodos) {
            items.add(new TodoItem(id++, t.content(), t.status()));
        }
        nextId.set(Math.max(id, 1));
    }

    public synchronized String formatForModel() {
        if (items.isEmpty()) {
            return "(no todos)";
        }
        return items.stream()
                .sorted(Comparator.comparingInt(TodoItem::id))
                .map(t -> t.id() + ". [" + t.status() + "] " + t.content())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
