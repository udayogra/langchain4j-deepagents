package com.deepagents.langchain4j.todos;

/**
 * Todo status values; API accepts LangChain-style snake_case ({@code pending}, {@code in_progress}, {@code completed})
 * or Java-style enum names.
 */
public enum TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED;

    /**
     * Parses model/tool JSON: snake_case (LangChain {@code Todo}) or {@code PENDING} / {@code IN_PROGRESS} / {@code COMPLETED}.
     */
    public static TodoStatus parseApi(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        String n = raw.trim().toLowerCase().replace(' ', '_').replace('-', '_');
        return switch (n) {
            case "pending" -> PENDING;
            case "in_progress" -> IN_PROGRESS;
            case "completed" -> COMPLETED;
            default -> {
                try {
                    yield valueOf(n.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "status must be pending, in_progress, or completed (or PENDING, IN_PROGRESS, COMPLETED)");
                }
            }
        };
    }
}
