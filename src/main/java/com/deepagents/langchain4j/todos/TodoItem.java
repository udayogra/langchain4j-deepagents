package com.deepagents.langchain4j.todos;

public record TodoItem(int id, String content, TodoStatus status) {

    public TodoItem withStatus(TodoStatus newStatus) {
        return new TodoItem(id, content, newStatus);
    }
}
