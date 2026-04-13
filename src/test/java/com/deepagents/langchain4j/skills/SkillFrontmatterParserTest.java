package com.deepagents.langchain4j.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkillFrontmatterParserTest {

    @Test
    void extractFrontmatter_parsesBlock() {
        String md =
                """
                ---
                name: my-skill
                description: Does a thing.
                ---

                # Body
                hello
                """;
        assertEquals(
                """
                name: my-skill
                description: Does a thing.""",
                SkillFrontmatterParser.extractFrontmatter(md));
    }

    @Test
    void extractFrontmatter_missingClosing_returnsNull() {
        assertNull(SkillFrontmatterParser.extractFrontmatter("---\nname: x\n"));
    }

    @Test
    void parse_readsNameDescriptionAndPath(@TempDir Path tmp) throws Exception {
        Path skillMd = tmp.resolve("SKILL.md");
        Files.writeString(
                skillMd,
                """
                ---
                name: custom
                description: Short summary.
                ---

                # More
                """);

        SkillMetadata m = SkillFrontmatterParser.parse(skillMd, "folder-name", "skills/custom/SKILL.md");
        assertEquals("custom", m.name());
        assertEquals("Short summary.", m.description());
        assertEquals("skills/custom/SKILL.md", m.readRelativePath());
    }

    @Test
    void parse_fallsBackToDirectoryNameWhenNameMissing(@TempDir Path tmp) throws Exception {
        Path skillMd = tmp.resolve("SKILL.md");
        Files.writeString(skillMd, "---\n---\n\n# x\n");

        SkillMetadata m = SkillFrontmatterParser.parse(skillMd, "dir-fallback", "p/SKILL.md");
        assertEquals("dir-fallback", m.name());
    }
}
