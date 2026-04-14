package com.deepagents.langchain4j.memory;

import com.deepagents.langchain4j.ResourceTexts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads optional agent memory files (e.g. {@code AGENTS.md}) from paths under the workspace and formats the block
 * injected into the system prompt, aligned with deepagentsjs {@code memory.ts} / <a href="https://agents.md/">agents.md</a>.
 *
 * <p>Each configured path must resolve inside the workspace root (same sandbox as {@code read_file} / {@code edit_file})
 * so the model can update memory with {@code edit_file} using the path shown in the prompt.
 */
public final class AgentMemoryLoader {

    /** Same cap as {@link com.deepagents.langchain4j.files.WorkspaceFileOperations#readFile} for consistency. */
    public static final int MAX_MEMORY_FILE_BYTES = 512_000;

    private AgentMemoryLoader() {}

    /**
     * When {@code sources} is empty, returns empty string (no injection). Otherwise loads files in order and returns the
     * full {@code memory_system_prompt.txt} with {@code {memory_contents}} replaced (possibly {@code (No memory loaded)}).
     */
    public static String buildPromptSection(Path workspace, List<Path> sources) throws IOException {
        Objects.requireNonNull(workspace, "workspace");
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        String memoryContents = formatMemoryContents(workspace, sources);
        String template = ResourceTexts.load("memory_system_prompt.txt");
        return template.replace("{memory_contents}", memoryContents);
    }

    /**
     * Formats loaded files like deepagentsjs {@code formatMemoryContents}: each section is {@code <displayPath>\\n<content>},
     * joined by blank lines, in {@code sources} order. Missing or oversize files are skipped.
     */
    public static String formatMemoryContents(Path workspace, List<Path> sources) throws IOException {
        Path root = workspace.toAbsolutePath().normalize();
        List<String> sections = new ArrayList<>();
        for (Path source : sources) {
            Path resolved = resolveUnderWorkspace(root, Objects.requireNonNull(source, "source"));
            if (!Files.isRegularFile(resolved)) {
                continue;
            }
            long size = Files.size(resolved);
            if (size > MAX_MEMORY_FILE_BYTES) {
                continue;
            }
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            String display = displayPathForModel(root, resolved);
            sections.add(display + "\n" + content);
        }
        if (sections.isEmpty()) {
            return "(No memory loaded)";
        }
        return String.join("\n\n", sections);
    }

    static Path resolveUnderWorkspace(Path workspaceRoot, Path source) throws IOException {
        Path normalizedSource = source.normalize();
        Path resolved =
                normalizedSource.isAbsolute()
                        ? normalizedSource
                        : workspaceRoot.resolve(normalizedSource).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException(
                    "Memory source escapes workspace: " + source + " (resolved to " + resolved + ")");
        }
        return resolved;
    }

    /**
     * Workspace-relative POSIX-style path for prompts and for {@code edit_file} (relative to workspace root).
     */
    static String displayPathForModel(Path workspaceRoot, Path resolvedFile) {
        Path rel = workspaceRoot.relativize(resolvedFile.normalize());
        String s = rel.toString().replace('\\', '/');
        return s.isEmpty() ? "." : s;
    }
}
