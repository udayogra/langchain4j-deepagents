package com.deepagents.langchain4j.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillsPromptFormatterTest {

    @Test
    void emptySkills_returnsEmptyString() {
        assertEquals("", SkillsPromptFormatter.formatSection(Path.of("/w"), List.of(Path.of("skills")), List.of()));
    }

    @Test
    void nonEmpty_containsNameAndReadPath(@TempDir Path workspace) {
        List<SkillMetadata> skills =
                List.of(new SkillMetadata("s1", "does work", "skills/s1/SKILL.md"));
        String section =
                SkillsPromptFormatter.formatSection(workspace, List.of(workspace.resolve("skills")), skills);
        assertTrue(section.contains("## Skills system"));
        assertTrue(section.contains("**s1**"));
        assertTrue(section.contains("`skills/s1/SKILL.md`"));
        assertTrue(section.contains("read_file"));
    }
}
