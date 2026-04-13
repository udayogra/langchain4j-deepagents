package com.deepagents.langchain4j.skills;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Progressive disclosure: append a compact skills catalog to the orchestrator (and optionally sub-agent) system prompts.
 * Full {@code SKILL.md} bodies are loaded via {@code read_file} when needed.
 */
public final class SkillsPromptFormatter {

    private SkillsPromptFormatter() {}

    /**
     * @param workspaceRoot used to print skill root locations relative to workspace
     * @param skillSourceRoots same roots passed to {@link SkillsScanner#scan}
     * @param skills         result of {@link SkillsScanner#scan}
     * @return empty string if {@code skills} is empty; otherwise a markdown block to append after the base system prompt
     */
    public static String formatSection(Path workspaceRoot, List<Path> skillSourceRoots, List<SkillMetadata> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        Path root = workspaceRoot.toAbsolutePath().normalize();
        String locations =
                skillSourceRoots.stream()
                        .filter(p -> p != null)
                        .map(
                                raw -> {
                                    Path src = raw.isAbsolute() ? raw.normalize() : root.resolve(raw).normalize();
                                    if (!src.startsWith(root)) {
                                        return raw.toString().replace('\\', '/');
                                    }
                                    return root.relativize(src).toString().replace('\\', '/');
                                })
                        .distinct()
                        .map(p -> "- `" + p + "/`")
                        .collect(Collectors.joining("\n"));

        StringBuilder bullets = new StringBuilder();
        for (SkillMetadata s : skills) {
            if (bullets.length() > 0) {
                bullets.append('\n');
            }
            bullets.append("- **")
                    .append(escapeMarkdownBoldFragment(s.name()))
                    .append("** — ")
                    .append(s.description())
                    .append(" Full instructions: `")
                    .append(s.readRelativePath())
                    .append("`");
        }

        return """
                ## Skills system

                Optional **skills** are folders under the workspace with domain-specific instructions. Below are short summaries only; \
                the full content lives in each skill’s `SKILL.md`.

                ### How to use skills

                1. Use the summaries to decide if a skill applies.
                2. If it applies, call **read_file** with the path shown to load the full `SKILL.md`, then follow those instructions.

                ### Skill locations (search roots)

                """
                + locations
                + """

                ### Available skills

                """
                + bullets
                + """

                **Note:** Without **read_file**, you cannot load full skill instructions.
                """;
    }

    /** Avoid breaking `**…**` if a skill name contained asterisks (unlikely). */
    private static String escapeMarkdownBoldFragment(String name) {
        return name.replace("*", "\\*");
    }
}
