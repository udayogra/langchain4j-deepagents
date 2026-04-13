package com.deepagents.langchain4j.skills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

/**
 * Discovers skills under one or more roots (each root is a directory of skill folders containing {@code SKILL.md}).
 * Multiple roots merge by skill {@code name}; later roots override earlier ones (same behavior as Deep Agents Python).
 */
public final class SkillsScanner {

    private SkillsScanner() {}

    /**
     * @param workspaceRoot sandbox root for file tools
     * @param skillSourceRoots directories to scan (relative paths resolved against {@code workspaceRoot}); each must stay
     *                         inside the workspace
     * @return ordered list of unique skills (by name), last root wins on duplicate names
     */
    public static List<SkillMetadata> scan(Path workspaceRoot, List<Path> skillSourceRoots) throws IOException {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        LinkedHashMap<String, SkillMetadata> byName = new LinkedHashMap<>();
        for (Path raw : skillSourceRoots) {
            if (raw == null) {
                continue;
            }
            Path src = raw.isAbsolute() ? raw.normalize() : root.resolve(raw).normalize();
            if (!src.startsWith(root)) {
                throw new IllegalArgumentException("Skill root must be inside workspace: " + raw);
            }
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(src)) {
                List<Path> dirs =
                        stream.filter(Files::isDirectory).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
                for (Path skillDir : dirs) {
                    Path skillMd = skillDir.resolve("SKILL.md");
                    if (!Files.isRegularFile(skillMd)) {
                        continue;
                    }
                    String rel = root.relativize(skillMd.toAbsolutePath().normalize()).toString().replace('\\', '/');
                    SkillMetadata meta = SkillFrontmatterParser.parse(skillMd, skillDir.getFileName().toString(), rel);
                    byName.put(meta.name(), meta);
                }
            }
        }
        return new ArrayList<>(byName.values());
    }
}
